// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ClassTreeNode extends BasePsiMemberNode<PsiClass> implements FileNodeWithNestedFileNodes {
  private final Collection<? extends AbstractTreeNode<?>> myNestedFileNodes;
  private boolean isAlwaysExpand;

  public ClassTreeNode(Project project, @NotNull PsiClass value, ViewSettings viewSettings) {
    this(project, value, viewSettings, ContainerUtil.emptyList());
  }

  public ClassTreeNode(Project project,
                       @NotNull PsiClass value,
                       ViewSettings viewSettings,
                       @NotNull Collection<? extends AbstractTreeNode<?>> nestedFileNodes) {
    super(project, value, viewSettings);
    myNestedFileNodes = nestedFileNodes;
  }

  @Override
  public @NotNull Collection<? extends AbstractTreeNode<?>> getNestedFileNodes() {
    return myNestedFileNodes;
  }

  @Override
  public Collection<AbstractTreeNode<?>> getChildrenImpl() {
    ArrayList<AbstractTreeNode<?>> result = new ArrayList<>(myNestedFileNodes);
    result.addAll(computeChildren(getValue(), this.getSettings(), this.getProject(), this.isShowInnerClasses()));
    return result;
  }

  @ApiStatus.Internal
  public static @NotNull List<AbstractTreeNode<?>> computeChildren(PsiClass parent,
                                                                   ViewSettings settings,
                                                                   Project project,
                                                                   boolean showInnerClasses) {
    List<AbstractTreeNode<?>> treeNodes = new ArrayList<>();
    if (parent != null) {
      try {
        boolean showMembers = settings.isShowMembers();
        if (showMembers || showInnerClasses) {
          for (PsiClass psi : parent.getInnerClasses()) {
            if (psi.isPhysical()) {
              treeNodes.add(new ClassTreeNode(project, psi, settings));
            }
          }
        }
        if (showMembers) {
          for (PsiMethod psi : parent.getMethods()) {
            if (psi.isPhysical()) {
              treeNodes.add(new PsiMethodNode(project, psi, settings));
            }
          }
          for (PsiField psi : parent.getFields()) {
            if (psi.isPhysical()) {
              treeNodes.add(new PsiFieldNode(project, psi, settings));
            }
          }
        }
      }
      catch (IndexNotReadyException ignore) {
      }
    }
    return treeNodes;
  }

  private boolean isShowInnerClasses() {
    boolean showInnerClasses = Registry.is("projectView.always.show.inner.classes", false);
    if (showInnerClasses) return true;
    VirtualFile file = getVirtualFile();
    return file != null && FileTypeRegistry.getInstance().isFileOfType(file, JavaClassFileType.INSTANCE);
  }

  @Override
  public void updateImpl(@NotNull PresentationData data) {
    final PsiClass aClass = getValue();
    if (aClass != null) {
      data.setPresentableText(aClass.getName());
    }
    isAlwaysExpand = getParentValue() instanceof PsiFile;
  }

  public boolean isTopLevel() {
    return getValue() != null && getValue().getParent()instanceof PsiFile;
  }


  @Override
  public boolean expandOnDoubleClick() {
    return false;
  }

  public PsiClass getPsiClass() {
    return getValue();
  }

  @Override
  public boolean isAlwaysExpand() {
    return isAlwaysExpand;
  }

  @Override
  public int getWeight() {
    return 20;
  }

  @Override
  public String getTitle() {
    final PsiClass psiClass = getValue();
    if (psiClass != null && psiClass.isValid()) {
      return psiClass.getQualifiedName();
    }
    return super.getTitle();
  }

  @Override
  protected boolean isMarkReadOnly() {
    return true;
  }

  @Override
  public int getTypeSortWeight(final boolean sortByType) {
    return sortByType ? 5 : 0;
  }

  @Override
  public Comparable getTypeSortKey() {
    return new ClassNameSortKey();
  }

  public static int getClassPosition(final PsiClass aClass) {
    if (aClass == null || !aClass.isValid()) {
      return 0;
    }
    try {
      int pos = aClass instanceof JspClass ? ElementPresentationUtil.CLASS_KIND_JSP : ElementPresentationUtil.getClassKind(aClass);
      //abstract class before concrete
      if (pos == ElementPresentationUtil.CLASS_KIND_CLASS || pos == ElementPresentationUtil.CLASS_KIND_EXCEPTION) {
        boolean isAbstract = aClass.hasModifierProperty(PsiModifier.ABSTRACT) && !aClass.isInterface();
        if (isAbstract) {
          pos --;
        }
      }
      return pos;
    }
    catch (IndexNotReadyException e) {
      return 0;
    }
  }

  private class ClassNameSortKey implements Comparable {
    @Override
    public int compareTo(final Object o) {
      if (!(o instanceof ClassNameSortKey rhs)) return 0;
      return getPosition() - rhs.getPosition();
    }

    int getPosition() {
      return getClassPosition(getValue());
    }
  }

  @Override
  public boolean shouldDrillDownOnEmptyElement() {
    return true;
  }

  @Override
  public boolean canRepresent(final Object element) {
    if (!mayContain(element)) return false;
    if (!isValid()) return false;
    return super.canRepresent(element) || canRepresent(getValue(), element);
  }

  private boolean canRepresent(final PsiClass psiClass, final Object element) {
    if (psiClass == null || !psiClass.isValid() || element == null) return false;

    final PsiFile parentFile = parentFileOf(psiClass);
    if (parentFile != null && (parentFile == element || element.equals(parentFile.getVirtualFile()))) return true;

    if (!getSettings().isShowMembers()) {
      if (element instanceof PsiElement && ((PsiElement)element).isValid()) {
        PsiFile elementFile = ((PsiElement)element).getContainingFile();
        if (elementFile != null && parentFile != null) {
          return elementFile.equals(parentFile);
        }
      }
    }

    return false;
  }

  private static @Nullable PsiFile parentFileOf(final PsiClass psiClass) {
    return psiClass.getContainingClass() == null ? psiClass.getContainingFile() : null;
  }
}
