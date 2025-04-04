// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.reference;

import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class RefElementImpl extends RefEntityImpl implements RefElement, WritableRefElement {
  protected static final Logger LOG = Logger.getInstance(RefElementImpl.class);

  private static final int IS_DELETED_MASK         = 0b10000; // 5th bit
  private static final int IS_INITIALIZED_MASK     = 0b100000; // 6th bit
  private static final int IS_REACHABLE_MASK       = 0b1000000; // 7th bit
  private static final int IS_ENTRY_MASK           = 0b10000000; // 8th bit
  private static final int IS_PERMANENT_ENTRY_MASK = 0b1_00000000; // 9th bit
  private static final int REFERENCES_BUILT_MASK   = 0b10_00000000; // 10th bit

  private final SmartPsiElementPointer<?> myID;

  private List<RefElement> myOutReferences; // guarded by this
  private List<RefElement> myInReferences; // guarded by this

  private String[] mySuppressions;

  protected RefElementImpl(@NotNull String name, @NotNull RefElement owner) {
    super(name, owner.getRefManager());
    myID = null;
  }

  protected RefElementImpl(PsiFile file, RefManager manager) {
    this(file.getName(), file, manager);
  }

  protected RefElementImpl(@NotNull String name, @NotNull PsiElement element, @NotNull RefManager manager) {
    super(name, manager);
    myID = SmartPointerManager.getInstance(manager.getProject()).createSmartPsiElementPointer(element);
  }

  protected boolean isDeleted() {
    return checkFlag(IS_DELETED_MASK);
  }

  @Override
  public boolean isValid() {
    if (isDeleted()) return false;
    return ReadAction.compute(() -> {
      if (getRefManager().getProject().isDisposed()) return false;

      //no need to check resolve in offline mode
      if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
        final PsiFile file = getContainingFile();
        return file != null && file.isPhysical();
      }

      final PsiElement element = getPsiElement();
      return element != null && element.isPhysical();
    });
  }

  @Override
  public @Nullable Icon getIcon(boolean expanded) {
    final PsiElement element = getPsiElement();
    if (element != null && element.isValid()) {
      return element.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS);
    }
    return null;
  }

  @Override
  public RefModule getModule() {
    return getOwner() instanceof RefElement e ? e.getModule() : null;
  }

  @Override
  public String getExternalName() {
    return getName();
  }

  @Override
  public @Nullable PsiElement getPsiElement() {
    return myID.getElement();
  }

  public @Nullable PsiFile getContainingFile() {
    return myID.getContainingFile();
  }

  public VirtualFile getVirtualFile() {
    return myID.getVirtualFile();
  }

  @Override
  public SmartPsiElementPointer<?> getPointer() {
    return myID;
  }

  @Override
  public synchronized @NotNull List<RefEntity> getChildren() {
    LOG.assertTrue(isInitialized());
    return super.getChildren();
  }

  @Override
  public boolean isReachable() {
    return checkFlag(IS_REACHABLE_MASK);
  }

  public void setReachable(boolean reachable) {
    setFlag(reachable, IS_REACHABLE_MASK);
  }

  @Override
  public synchronized boolean isReferenced() {
    return myInReferences != null;
  }

  public boolean hasSuspiciousCallers() {
    for (RefElement refCaller : getInReferences()) {
      if (((RefElementImpl)refCaller).isSuspicious()) return true;
    }

    return false;
  }

  @Override
  public synchronized @NotNull Collection<RefElement> getOutReferences() {
    return (myOutReferences == null) ? ContainerUtil.emptyList() : Collections.unmodifiableList(myOutReferences);
  }

  @Override
  public synchronized @NotNull Collection<RefElement> getInReferences() {
    return (myInReferences == null) ? ContainerUtil.emptyList() : Collections.unmodifiableList(myInReferences);
  }

  @Override
  public synchronized void addInReference(RefElement refElement) {
    List<RefElement> inReferences = myInReferences;
    if (inReferences == null) {
      myInReferences = inReferences = new ArrayList<>(1);
    }
    if (!inReferences.contains(refElement)) {
      inReferences.add(refElement);
    }
  }

  private synchronized void removeInReference(RefElement refElement) {
    if (myInReferences == null) return;
    myInReferences.remove(refElement);
    if (myInReferences.isEmpty()) {
      myInReferences = null;
    }
  }

  @Override
  public synchronized void addOutReference(RefElement refElement) {
    List<RefElement> outReferences = myOutReferences;
    if (outReferences == null) {
      myOutReferences = outReferences = new ArrayList<>(1);
    }
    if (!outReferences.contains(refElement)) {
      outReferences.add(refElement);
    }
  }

  private synchronized void removeOutReference(RefElement refElement) {
    if (myOutReferences == null) return;
    myOutReferences.remove(refElement);
    if (myOutReferences.isEmpty()) {
      myOutReferences = null;
    }
  }

  @Override
  public boolean areReferencesBuilt() {
    return checkAndSetFlag(REFERENCES_BUILT_MASK);
  }

  public void setEntry(boolean entry) {
    setFlag(entry, IS_ENTRY_MASK);
  }

  @Override
  public boolean isEntry() {
    return checkFlag(IS_ENTRY_MASK);
  }

  @Override
  public boolean isPermanentEntry() {
    return checkFlag(IS_PERMANENT_ENTRY_MASK);
  }

  @Override
  public @NotNull RefElement getContainingEntry() {
    return this;
  }

  public void setPermanentEntry(boolean permanentEntry) {
    setFlag(permanentEntry, IS_PERMANENT_ENTRY_MASK);
  }

  public boolean isSuspicious() {
    return !isReachable();
  }

  public void referenceRemoved() {
    setFlag(true, IS_DELETED_MASK);
    if (getOwner() != null) {
      getOwner().removeChild(this);
    }

    for (RefElement refCallee : getOutReferences()) {
      ((RefElementImpl)refCallee).removeInReference(this);
    }

    for (RefElement refCaller : getInReferences()) {
      ((RefElementImpl)refCaller).removeOutReference(this);
    }
  }

  public @Nullable String getURL() {
    final PsiElement element = getPsiElement();
    if (element == null || !element.isPhysical()) return null;
    final PsiFileSystemItem containingFile = element instanceof PsiFileSystemItem item ? item : element.getContainingFile();
    if (containingFile == null) return null;
    final VirtualFile virtualFile = containingFile.getVirtualFile();
    if (virtualFile == null) return null;
    return element instanceof PsiFileSystemItem
           ? virtualFile.getUrl()
           : virtualFile.getUrl() + "#" + element.getTextOffset();
  }

  protected abstract void initialize();

  @Override
  public boolean isInitialized() {
    return checkFlag(IS_INITIALIZED_MASK);
  }

  public synchronized void setInitialized(boolean initialized) {
    setFlag(initialized, IS_INITIALIZED_MASK);
  }

  @Override
  public final synchronized void initializeIfNeeded() {
    if (isInitialized()) {
      return;
    }
    initialize();
    setInitialized(true);
    getRefManager().fireNodeInitialized(this);
  }

  @Override
  public void addSuppression(String text) {
    mySuppressions = text.split("[, ]");
  }

  @Override
  public boolean isSuppressed(String @NotNull ... toolIds) {
    if (mySuppressions != null) {
      for (@NonNls String suppression : mySuppressions) {
        if (ArrayUtil.contains(suppression, toolIds)) {
          return true;
        }
        if (suppression.equalsIgnoreCase(SuppressionUtil.ALL)) {
          return true;
        }
      }
    }
    return getOwner() instanceof RefElementImpl e && e.isSuppressed(toolIds);
  }
}
