/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.analysis.reflection;

import java.util.ArrayList;
import java.util.Iterator;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.cfg.InducedCFG;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.SSAContextInterpreter;
import com.ibm.wala.ipa.summaries.SyntheticIR;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSALoadClassInstruction;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SSAThrowInstruction;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.EmptyIterator;
import com.ibm.wala.util.collections.NonNullSingletonIterator;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.strings.Atom;

/**
 * An {@link SSAContextInterpreter} specialized to interpret Class.forName in a {@link JavaTypeContext} which
 * represents the point-type of the class object created by the call.
 * 
 * @author pistoia
 */
public class ForNameContextInterpreter implements SSAContextInterpreter {

  public final static Atom forNameAtom = Atom.findOrCreateUnicodeAtom("forName");

  private final static Descriptor forNameDescriptor = Descriptor.findOrCreateUTF8("(Ljava/lang/String;)Ljava/lang/Class;");

  public final static MethodReference FOR_NAME_REF = MethodReference.findOrCreate(TypeReference.JavaLangClass, forNameAtom,
      forNameDescriptor);

  public IR getIR(CGNode node) {
    if (node == null) {
      throw new IllegalArgumentException("node is null");
    }
    if (Assertions.verifyAssertions) {
      Assertions._assert(understands(node));
    }
    IR result = makeIR(node.getMethod(), (JavaTypeContext) node.getContext());
    return result;
  }

  public int getNumberOfStatements(CGNode node) {
    if (Assertions.verifyAssertions) {
      Assertions._assert(understands(node));
    }
    return getIR(node).getInstructions().length;
  }

  public boolean understands(CGNode node) {
    if (node == null) {
      throw new IllegalArgumentException("node is null");
    }
    if (!(node.getContext() instanceof JavaTypeContext))
      return false;
    return node.getMethod().getReference().equals(FOR_NAME_REF);
  }

  public Iterator<NewSiteReference> iterateNewSites(CGNode node) {
    if (node == null) {
      throw new IllegalArgumentException("node is null");
    }
    if (Assertions.verifyAssertions) {
      Assertions._assert(understands(node));
    }
    JavaTypeContext context = (JavaTypeContext) node.getContext();
    TypeReference tr = context.getType().getTypeReference();
    if (tr != null) {
      return new NonNullSingletonIterator<NewSiteReference>(NewSiteReference.make(0, tr));
    }
    return EmptyIterator.instance();
  }

  public Iterator<CallSiteReference> iterateCallSites(CGNode node) {
    if (Assertions.verifyAssertions) {
      Assertions._assert(understands(node));
    }
    return EmptyIterator.instance();
  }

  private SSAInstruction[] makeStatements(JavaTypeContext context) {
    ArrayList<SSAInstruction> statements = new ArrayList<SSAInstruction>();
    int nextLocal = 1;
    int retValue = nextLocal++;
    TypeReference tr = context.getType().getTypeReference();
    if (tr != null) {
      SSALoadClassInstruction l = new SSALoadClassInstruction(retValue, tr);
      statements.add(l);
      SSAReturnInstruction R = new SSAReturnInstruction(retValue, false);
      statements.add(R);
    } else {
      SSAThrowInstruction t = new SSAThrowInstruction(retValue);
      statements.add(t);
    }
    SSAInstruction[] result = new SSAInstruction[statements.size()];
    Iterator<SSAInstruction> it = statements.iterator();
    for (int i = 0; i < result.length; i++) {
      result[i] = it.next();
    }
    return result;
  }

  private IR makeIR(IMethod method, JavaTypeContext context) {
    SSAInstruction instrs[] = makeStatements(context);
    return new SyntheticIR(method, context, new InducedCFG(instrs, method, context), instrs, SSAOptions.defaultOptions(), null);
  }

  public boolean recordFactoryType(CGNode node, IClass klass) {
    return false;
  }

  public Iterator<FieldReference> iterateFieldsRead(CGNode node) {
    return EmptyIterator.instance();
  }

  public Iterator<FieldReference> iterateFieldsWritten(CGNode node) {
    return EmptyIterator.instance();
  }

  public ControlFlowGraph<ISSABasicBlock> getCFG(CGNode N) {
    return getIR(N).getControlFlowGraph();
  }

  public DefUse getDU(CGNode node) {
    return new DefUse(getIR(node));
  }
}