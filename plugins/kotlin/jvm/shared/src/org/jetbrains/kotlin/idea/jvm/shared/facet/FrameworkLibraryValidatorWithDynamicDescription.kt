// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.shared.facet

import com.intellij.facet.impl.ui.libraries.LibrariesValidatorContext
import com.intellij.facet.ui.FacetConfigurationQuickFix
import com.intellij.facet.ui.FacetValidatorsManager
import com.intellij.facet.ui.SlowFacetEditorValidator
import com.intellij.facet.ui.ValidationResult
import com.intellij.facet.ui.libraries.FrameworkLibraryValidator
import com.intellij.ide.IdeBundle
import com.intellij.openapi.roots.ui.configuration.libraries.AddCustomLibraryDialog
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryPresentationManager
import org.jetbrains.kotlin.idea.facet.KotlinVersionInfoProvider
import org.jetbrains.kotlin.idea.jvm.shared.KotlinJvmBundle
import org.jetbrains.kotlin.idea.projectConfiguration.getLibraryDescription
import org.jetbrains.kotlin.platform.IdePlatformKind
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.platform.impl.isCommon
import javax.swing.JComponent

// Based on com.intellij.facet.impl.ui.libraries.FrameworkLibraryValidatorImpl
class FrameworkLibraryValidatorWithDynamicDescription(
    private val context: LibrariesValidatorContext,
    private val validatorsManager: FacetValidatorsManager,
    private val libraryCategoryName: String,
    private val getPlatform: () -> TargetPlatform?
) : FrameworkLibraryValidator(), SlowFacetEditorValidator {
    private val IdePlatformKind.libraryDescription: CustomLibraryDescription?
        get() = getLibraryDescription(context.module.project, this)

    private fun checkLibraryIsConfigured(platform: IdePlatformKind): Boolean {
        // TODO: propose to configure kotlin-stdlib-common once it's available
        if (platform.isCommon) return true

        if (KotlinVersionInfoProvider.EP_NAME.extensionList.any {
                it.getLibraryVersionsSequence(context.module, platform, context.rootModel).any()
            }
        ) return true

        val libraryDescription = platform.libraryDescription ?: return true
        val libraryKinds = libraryDescription.suitableLibraryKinds
        var found = false
        val presentationManager = LibraryPresentationManager.getInstance()
        context.rootModel
            .orderEntries()
            .using(context.modulesProvider)
            .recursively()
            .librariesOnly()
            .forEachLibrary { library ->
                if (presentationManager.isLibraryOfKind(library, context.librariesContainer, libraryKinds)) {
                    found = true
                }
                !found
            }
        return found
    }

    override fun check(): ValidationResult {
        val targetPlatform = getPlatform() ?: return ValidationResult(KotlinJvmBundle.message("no.target.platforms.selected"))

        if (checkLibraryIsConfigured(targetPlatform.idePlatformKind)) {
            val conflictingPlatforms = IdePlatformKind.ALL_KINDS
                .filter {
                    !it.isCommon && it.name != targetPlatform.idePlatformKind.name
                            && it.libraryDescription != null && checkLibraryIsConfigured(it)
                }

            if (conflictingPlatforms.isNotEmpty()) {
                val platformText = conflictingPlatforms.mapTo(LinkedHashSet()) { it.name }.joinToString()
                return ValidationResult(
                    KotlinJvmBundle.message(
                        "libraries.for.the.following.platform.are.also.present.in.the.module.dependencies.0",
                        platformText
                    )
                )
            }

            return ValidationResult.OK
        }

        return ValidationResult(
            KotlinJvmBundle.message("label.missed.libraries.text", libraryCategoryName),
            LibrariesQuickFix(targetPlatform.idePlatformKind.libraryDescription!!)
        )
    }

    private inner class LibrariesQuickFix(
        private val myDescription: CustomLibraryDescription
    ) : FacetConfigurationQuickFix(IdeBundle.message("button.fix")) {
        override fun run(place: JComponent) {
            val dialog = AddCustomLibraryDialog.createDialog(
                myDescription, context.librariesContainer,
                context.module, context.modifiableRootModel,
                null
            )
            dialog.show()
            validatorsManager.validate()
        }
    }
}
