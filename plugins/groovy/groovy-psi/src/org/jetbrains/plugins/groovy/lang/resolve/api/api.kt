// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.api

import com.intellij.psi.PsiType

typealias Arguments = List<Argument>

typealias Applicabilities = Map<Argument, ApplicabilityData>

data class ApplicabilityData(val expectedType: PsiType?, val applicability: Applicability)