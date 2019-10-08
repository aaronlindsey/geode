/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.metrics.functionexecutions;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.geode.cache.execute.Function;
import org.apache.geode.internal.InternalEntity;
import org.apache.geode.internal.cache.execute.metrics.InternalFunctionClassifier;
import org.apache.geode.management.internal.ManagementFunction;

public class InternalFunctionAcceptanceTest {
  private static final Map<Class<? extends Function>, String> idsForFunctionsWithoutNoArgConstructor = new HashMap<>();

  private InternalFunctionClassifier internalFunctionClassifier = new InternalFunctionClassifier();

  @BeforeClass
  public static void setUp() {
    idsForFunctionsWithoutNoArgConstructor.put(ManagementFunction.class,
        new ManagementFunction(null).getId());
  }

  @Test
  public void classifierReturnsIsInternal_forInternalFunctionIds() {
    List<String> internalFunctionIds = getIdsForFunctionsOfType(FunctionType.INTERNAL);

    assertThat(internalFunctionIds)
        .allMatch(internalFunctionClassifier::isInternal);
  }

  @Test
  public void classifierReturnsIsNotInternal_forNonInternalFunctionIds() {
    List<String> nonInternalFunctionIds = getIdsForFunctionsOfType(FunctionType.NON_INTERNAL);

    assertThat(nonInternalFunctionIds)
        .noneMatch(internalFunctionClassifier::isInternal);
  }

  private List<String> getIdsForFunctionsOfType(FunctionType functionType) {
    ClassGraph fastClasspathScanner = new ClassGraph()
        .removeTemporaryFilesAfterScan()
        .enableClassInfo();

    try (ScanResult scanResult = fastClasspathScanner.scan(1)) {
      ClassInfoList functionClasses =
          scanResult.getClassesImplementing(Function.class.getName());
      ClassInfoList internalEntityClasses =
          scanResult.getClassesImplementing(InternalEntity.class.getName());

      ClassInfoList resultClasses = FunctionType.INTERNAL == functionType
          ? functionClasses.intersect(internalEntityClasses)
          : functionClasses.exclude(internalEntityClasses);

      return resultClasses.stream()
          .filter(ci -> !ci.isAbstract())
          .filter(ci -> !ci.isInterface())
          .map(ClassInfo::loadClass)
          .map(InternalFunctionAcceptanceTest::getIdForFunctionUnchecked)
          .collect(toList());
    }
  }

  @SuppressWarnings("unchecked")
  private static String getIdForFunctionUnchecked(Class<?> functionClass) {
    return getIdForFunction((Class<? extends Function>) functionClass);
  }

  private static String getIdForFunction(Class<? extends Function> functionClass) {
    if (idsForFunctionsWithoutNoArgConstructor.containsKey(functionClass)) {
      return idsForFunctionsWithoutNoArgConstructor.get(functionClass);
    }

    try {
      Constructor<? extends Function> constructor = functionClass.getDeclaredConstructor();
      if (!constructor.isAccessible()) {
        constructor.setAccessible(true);
      }
      return constructor.newInstance().getId();
    } catch (Exception e) {
      Object ignored = fail("Failed to create instance of class: " + functionClass.getName(), e);
    }

    // Impossible to hit this because Assertions.fail() will throw.
    return null;
  }

  private enum FunctionType {
    INTERNAL,
    NON_INTERNAL
  }
}
