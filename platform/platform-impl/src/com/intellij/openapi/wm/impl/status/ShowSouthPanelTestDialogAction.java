// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.OptionAction;
import com.intellij.ui.components.panels.Wrapper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;

@ApiStatus.Internal
@SuppressWarnings("HardCodedStringLiteral")
public final class ShowSouthPanelTestDialogAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    new MyDialogWrapper(e.getProject()).show();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static final class MyDialogWrapper extends DialogWrapper {
    private final Wrapper mySouthPanel = new Wrapper();

    private final JButton myRefresh = new JButton("Refresh");
    private final JCheckBox myHasOKAction = new JCheckBox("OK action", true);
    private final JCheckBox myHasCancelAction = new JCheckBox("Cancel action", true);
    private final JCheckBox myHasHelpAction = new JCheckBox("Help action", true);
    private final JCheckBox myHasOptionAction = new JCheckBox("Option action", false);
    private final JCheckBox myHasLeftAction = new JCheckBox("Left action", true);
    private final JCheckBox myHasDoNotShowCheckbox = new JCheckBox("'Do not show' checkbox", true);
    private final JCheckBox myCompact = new JCheckBox("Compact style", false);
    private final JCheckBox myErrorText = new JCheckBox("Error text", false);
    private final JCheckBox myMoveErrorTextToButtons = new JCheckBox("Move error text to the buttons", false);
    private final JCheckBox myCenterButtons = new JCheckBox("Center buttons", false);

    MyDialogWrapper(Project project) {
      super(project);
      init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
      JPanel panel = new JPanel();
      panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

      myRefresh.addActionListener(e -> refreshSouthPanel());

      myErrorText.addActionListener(e -> setErrorText(myErrorText.isSelected() ? "Error text" : null, myErrorText));
      myCenterButtons.addActionListener(e -> setButtonsAlignment(myCenterButtons.isSelected() ? SwingConstants.CENTER : SwingConstants.RIGHT));

      panel.add(myRefresh);
      panel.add(myHasOKAction);
      panel.add(myHasCancelAction);
      panel.add(myHasHelpAction);
      panel.add(myHasOptionAction);
      panel.add(myHasLeftAction);
      panel.add(myHasDoNotShowCheckbox);
      panel.add(myCompact);
      panel.add(myErrorText);
      panel.add(myMoveErrorTextToButtons);
      panel.add(myCenterButtons);

      return panel;
    }

    @Override
    protected JComponent createSouthPanel() {
      refreshSouthPanel();
      return mySouthPanel;
    }

    private void refreshSouthPanel() {
      mySouthPanel.setContent(super.createSouthPanel());
    }

    @Override
    protected Action @NotNull [] createActions() {
      return new Action[]{
        myHasOKAction.isSelected() ? getOKAction() : null,
        myHasCancelAction.isSelected() ? getCancelAction() : null,
        myHasHelpAction.isSelected() ? getHelpAction() : null,
        myHasOptionAction.isSelected() ? new MyOptionAction() : null,
      };
    }

    @Override
    protected Action @NotNull [] createLeftSideActions() {
      return myHasLeftAction.isSelected() ? new Action[]{new MyAction("Left")} : new Action[0];
    }

    @Override
    protected @NotNull DialogStyle getStyle() {
      return myCompact.isSelected() ? DialogStyle.COMPACT : DialogStyle.NO_STYLE;
    }

    @Override
    protected @Nullable JComponent createDoNotAskCheckbox() {
      return myHasDoNotShowCheckbox.isSelected() ? new JCheckBox("Do not show again") : null;
    }

    @Override
    protected boolean shouldAddErrorNearButtons() {
      return myMoveErrorTextToButtons.isSelected();
    }

    private static final class MyOptionAction extends MyAction implements OptionAction {
      private final Action[] myActions;

      MyOptionAction() {
        super("Option Button");
        myActions = new Action[]{
          new MyAction("Option #1"),
          new MyAction("Option #2"),
          new MyAction("Option #3")
        };
      }

      @Override
      public Action @NotNull [] getOptions() {
        return myActions;
      }
    }

    private static class MyAction extends AbstractAction {
      MyAction(String name) {
        super(name);
      }

      @Override
      public void actionPerformed(ActionEvent e) {
      }
    }
  }
}
