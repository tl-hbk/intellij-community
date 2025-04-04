// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.introduce.introduceParameter

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.ui.JBColor
import com.intellij.ui.NonFocusableCheckBox
import org.jetbrains.kotlin.idea.base.psi.unifier.toRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinValVar
import org.jetbrains.kotlin.idea.refactoring.introduce.AbstractKotlinInplaceIntroducer
import org.jetbrains.kotlin.idea.refactoring.introduce.TYPE_REFERENCE_VARIABLE_NAME
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import java.awt.Color
import javax.swing.JCheckBox

abstract class KotlinInplaceParameterIntroducerBase<KotlinType, Descriptor>(
    val originalDescriptor: IntroduceParameterDescriptor<Descriptor>,
    val parameterType: KotlinType,
    val suggestedNames: Array<out String>,
    project: Project,
    editor: Editor
) : AbstractKotlinInplaceIntroducer<KtParameter>(
    null,
    originalDescriptor.originalRange.elements.single() as KtExpression,
    originalDescriptor.occurrencesToReplace.map { it.elements.single() as KtExpression }.toTypedArray(),
    INTRODUCE_PARAMETER,
    project,
    editor
) {
    companion object {
        private val LOG = Logger.getInstance(KotlinInplaceParameterIntroducerBase::class.java)
    }

    enum class PreviewDecorator {
        FOR_ADD {
            override val textAttributes: TextAttributes = with(TextAttributes()) {
                effectType = EffectType.ROUNDED_BOX
                effectColor = JBColor.RED
                this
            }
        },

        FOR_REMOVAL {
            override val textAttributes: TextAttributes = with(TextAttributes()) {
                effectType = EffectType.STRIKEOUT
                effectColor = Color.BLACK
                this
            }
        };

        protected abstract val textAttributes: TextAttributes

        fun applyToRange(range: TextRange, markupModel: MarkupModel) {
            markupModel.addRangeHighlighter(
                range.startOffset,
                range.endOffset,
                0,
                textAttributes,
                HighlighterTargetArea.EXACT_RANGE
            )
        }
    }

    private inner class Preview(addedParameter: KtParameter?, currentName: String?) {
        private val _rangesToRemove = ArrayList<TextRange>()

        var addedRange: TextRange? = null
            private set

        var text: String = ""
            private set

        val rangesToRemove: List<TextRange> get() = _rangesToRemove

        init {
            val templateState = TemplateManagerImpl.getTemplateState(myEditor)
            val currentType = if (templateState?.template != null) {
                templateState.getVariableValue(TYPE_REFERENCE_VARIABLE_NAME)?.text
            } else null

            val builder = StringBuilder()

            with(descriptor) {
                (callable as? KtFunction)?.receiverTypeReference?.let { receiverTypeRef ->
                    builder.append(receiverTypeRef.text).append('.')
                    if (!descriptor.withDefaultValue && receiverTypeRef in parametersToRemove) {
                        _rangesToRemove.add(TextRange(0, builder.length))
                    }
                }

                builder.append(callable.name)

                val parameters = callable.getValueParameters()
                builder.append("(")
                for (i in parameters.indices) {
                    val parameter = parameters[i]

                    val parameterText = if (parameter == addedParameter) {
                        val parameterName = currentName ?: parameter.name?.quoteIfNeeded()
                        val parameterType = currentType ?: parameter.typeReference!!.text
                        descriptor = descriptor.copy(newParameterName = parameterName!!, newParameterTypeText = parameterType)
                        val modifier = if (valVar != KotlinValVar.None) "${valVar.keywordName} " else ""
                        val argumentValue = newArgumentValue
                        val defaultValue = if (withDefaultValue && argumentValue != null) {
                            " = ${if (argumentValue is KtProperty) argumentValue.name else argumentValue.text}"
                        } else ""

                        "$modifier$parameterName: $parameterType$defaultValue"
                    } else {
                        parameter.allChildren.toList()
                            .dropLastWhile { it is PsiComment || it is PsiWhiteSpace }
                            .joinToString(separator = "") { it.text }
                    }

                    builder.append(parameterText)

                    val range = TextRange(builder.length - parameterText.length, builder.length)
                    if (parameter == addedParameter) {
                        addedRange = range
                    } else if (!descriptor.withDefaultValue && parameter in parametersToRemove) {
                        _rangesToRemove.add(range)
                    }

                    if (i < parameters.lastIndex) {
                        builder.append(", ")
                    }
                }
                builder.append(")")

                if (addedRange == null) {
                    LOG.error("Added parameter not found: ${callable.getElementTextWithContext()}")
                }
            }

            text = builder.toString()
        }
    }

    private var descriptor = originalDescriptor
    private var replaceAllCheckBox: JCheckBox? = null

    init {
        initFormComponents {
            addComponent(previewComponent)

            val defaultValueCheckBox = NonFocusableCheckBox(KotlinBundle.message("checkbox.text.introduce.default.value"))
            defaultValueCheckBox.isSelected = descriptor.withDefaultValue
            defaultValueCheckBox.addActionListener {
                descriptor = descriptor.copy(withDefaultValue = defaultValueCheckBox.isSelected)
                updateTitle(variable)
            }
            addComponent(defaultValueCheckBox)

            val occurrenceCount = descriptor.occurrencesToReplace.size
            if (occurrenceCount > 1) {
                val replaceAllCheckBox = NonFocusableCheckBox(
                    KotlinBundle.message("checkbox.text.replace.all.occurrences.0", occurrenceCount))
                replaceAllCheckBox.isSelected = true
                addComponent(replaceAllCheckBox)
                this@KotlinInplaceParameterIntroducerBase.replaceAllCheckBox = replaceAllCheckBox
            }
        }
    }

    override fun getActionName() = "IntroduceParameter"

    override fun checkLocalScope() = descriptor.callable

    override fun getVariable() = originalDescriptor.callable.getValueParameters().lastOrNull()

    override fun suggestNames(replaceAll: Boolean, variable: KtParameter?) = suggestedNames

    override fun getInitialName(): String? {
        if (myInitialName == null) {
            val variable = getVariable()
            if (variable != null) {
                return variable.name?.quoteIfNeeded()
            }
            LOG.error("Initial name should be provided")
            return ""
        }
        return myInitialName
    }

    override fun createFieldToStartTemplateOn(replaceAll: Boolean, names: Array<out String>): KtParameter {
        return runWriteAction {
            with(descriptor) {
                val parameterList = callable.getValueParameterList()
                    ?: (callable as KtClass).createPrimaryConstructorParameterListIfAbsent()
                val parameter = KtPsiFactory(myProject).createParameter("$newParameterName: $newParameterTypeText")
                parameterList.addParameter(parameter)
            }
        }
    }

    override fun deleteTemplateField(psiField: KtParameter) {
        if (psiField.isValid) {
            (psiField.parent as? KtParameterList)?.removeParameter(psiField)
        }
    }

    override fun isReplaceAllOccurrences() = replaceAllCheckBox?.isSelected ?: true

    override fun setReplaceAllOccurrences(allOccurrences: Boolean) {
        replaceAllCheckBox?.isSelected = allOccurrences
    }

    override fun getComponent() = myWholePanel

    override fun updateTitle(addedParameter: KtParameter?, currentName: String?) {
        val preview = Preview(addedParameter, currentName)

        val document = previewEditor.document
        runWriteAction { document.setText(preview.text) }

        val markupModel = DocumentMarkupModel.forDocument(document, myProject, true)
        markupModel.removeAllHighlighters()
        preview.rangesToRemove.forEach { PreviewDecorator.FOR_REMOVAL.applyToRange(it, markupModel) }
        preview.addedRange?.let { PreviewDecorator.FOR_ADD.applyToRange(it, markupModel) }
        revalidate()
    }

    override fun getRangeToRename(element: PsiElement): TextRange {
        if (element is KtProperty) return element.nameIdentifier!!.textRange.shiftRight(-element.startOffset)
        return super.getRangeToRename(element)
    }

    override fun createMarker(element: PsiElement): RangeMarker {
        if (element is KtProperty) return super.createMarker(element.nameIdentifier)
        return super.createMarker(element)
    }

    override fun performIntroduce() {
        performRefactoring(getDescriptorToRefactor(isReplaceAllOccurrences))
    }

    protected fun getDescriptorToRefactor(replaceAll: Boolean): IntroduceParameterDescriptor<Descriptor> {
        val originalRange = expr.toRange()
        return descriptor.copy(
            originalRange = originalRange,
            occurrencesToReplace = if (replaceAll) occurrences.map { it.toRange() } else listOf(originalRange),
            argumentValue = expr!!
        )
    }

    abstract fun performRefactoring(descriptor: IntroduceParameterDescriptor<Descriptor>)
    abstract fun switchToDialogUI()
}
