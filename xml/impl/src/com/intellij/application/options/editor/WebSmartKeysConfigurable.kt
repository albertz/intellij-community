// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.BoundCompositeConfigurable
import com.intellij.openapi.options.ConfigurableEP
import com.intellij.openapi.options.ConfigurableWithOptionDescriptors
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.*
import com.intellij.xml.XmlBundle

private val model = WebEditorOptions.getInstance()
val myAutomaticallyInsertClosingTagCheckBox = CheckboxDescriptor("Insert closing tag on tag completion",
                                                                 PropertyBinding(model::isAutomaticallyInsertClosingTag,
                                                                                 model::setAutomaticallyInsertClosingTag))
val myAutomaticallyInsertRequiredAttributesCheckBox = CheckboxDescriptor("Insert required attributes on tag completion",
                                                                         PropertyBinding(model::isAutomaticallyInsertClosingTag,
                                                                                         model::setAutomaticallyInsertClosingTag))
val myAutomaticallyInsertRequiredSubTagsCheckBox = CheckboxDescriptor("Insert required subtags on tag completion",
                                                                      PropertyBinding(model::isAutomaticallyInsertClosingTag,
                                                                                      model::setAutomaticallyInsertClosingTag))
val myAutomaticallyStartAttributeAfterCheckBox = CheckboxDescriptor("Start attribute on tag completion",
                                                                    PropertyBinding(model::isAutomaticallyStartAttribute,
                                                                                    model::setAutomaticallyStartAttribute))
val myAddQuotasForAttributeValue = CheckboxDescriptor("Add quotes for attribute value on typing '=' and attribute completion",
                                                      PropertyBinding(model::isInsertQuotesForAttributeValue,
                                                                      model::setInsertQuotesForAttributeValue))
val myAutoCloseTagCheckBox = CheckboxDescriptor("Auto-close tag on typing '</'",
                                                PropertyBinding(model::isAutoCloseTag, model::setAutoCloseTag))
val mySyncTagEditing = CheckboxDescriptor("Simultaneous '<tag></tag>' editing",
                                          PropertyBinding(model::isSyncTagEditing, model::setSyncTagEditing))
val mySelectWholeCssIdentifierOnDoubleClick = CheckboxDescriptor("Select whole CSS identifiers on double click",
                                                                 PropertyBinding(model::isSelectWholeCssIdentifierOnDoubleClick,
                                                                                 model::setSelectWholeCssIdentifierOnDoubleClick))

val webEditorOptionDescriptors = listOf(
      myAutomaticallyInsertClosingTagCheckBox
    , myAutomaticallyInsertRequiredAttributesCheckBox
    , myAutomaticallyInsertRequiredSubTagsCheckBox
    , myAutomaticallyStartAttributeAfterCheckBox
    , myAddQuotasForAttributeValue
    , myAutoCloseTagCheckBox
    , mySyncTagEditing
    , mySelectWholeCssIdentifierOnDoubleClick
).map(CheckboxDescriptor::asOptionDescriptor)

class WebSmartKeysConfigurable(val model: WebEditorOptions) : BoundCompositeConfigurable<UnnamedConfigurable>("HTML/CSS"), ConfigurableWithOptionDescriptors {
  override fun createPanel(): DialogPanel {
    return panel {
      row {
        titledRow(XmlBundle.message("xml.editor.options.misc.title")) {
          row {
            checkBox(myAutomaticallyInsertClosingTagCheckBox)
          }
          row {
            checkBox(myAutomaticallyInsertRequiredAttributesCheckBox)
          }
          row {
            checkBox(myAutomaticallyInsertRequiredSubTagsCheckBox)
          }
          row {
            checkBox(myAutomaticallyStartAttributeAfterCheckBox)
          }
          row {
            checkBox(myAddQuotasForAttributeValue)
          }
          row {
            checkBox(myAutoCloseTagCheckBox)
          }
          row {
            checkBox(mySyncTagEditing)
          }
        }
      }
      row {
        titledRow("CSS") {
          row {
            checkBox(mySelectWholeCssIdentifierOnDoubleClick)
          }
        }
      }
      for (configurable in configurables) {
        row {
          configurable.createComponent()?.invoke(growX)
        }
      }
    }
  }

  override fun getOptionDescriptors(configurableId: String) = webEditorOptionDescriptors

  override fun createConfigurables(): List<UnnamedConfigurable> {
    return ConfigurableWrapper.createConfigurables(EP_NAME)
  }

  companion object {
    val EP_NAME = ExtensionPointName.create<WebSmartKeysConfigurableEP>("com.intellij.webSmartKeysConfigurable")
  }
}

class WebSmartKeysConfigurableEP : ConfigurableEP<UnnamedConfigurable>()
