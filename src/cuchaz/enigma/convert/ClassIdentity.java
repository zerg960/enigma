/*******************************************************************************
 * Copyright (c) 2014 Jeff Martin.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Jeff Martin - initial API and implementation
 ******************************************************************************/
package cuchaz.enigma.convert;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import javassist.CannotCompileException;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.Descriptor;
import javassist.bytecode.Opcode;
import javassist.expr.ConstructorCall;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import javassist.expr.MethodCall;
import javassist.expr.NewExpr;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;

import cuchaz.enigma.Constants;
import cuchaz.enigma.Util;
import cuchaz.enigma.analysis.ClassImplementationsTreeNode;
import cuchaz.enigma.analysis.EntryReference;
import cuchaz.enigma.analysis.JarIndex;
import cuchaz.enigma.bytecode.ConstPoolEditor;
import cuchaz.enigma.bytecode.InfoType;
import cuchaz.enigma.bytecode.accessors.ConstInfoAccessor;
import cuchaz.enigma.convert.ClassNamer.SidedClassNamer;
import cuchaz.enigma.mapping.BehaviorEntry;
import cuchaz.enigma.mapping.ClassEntry;
import cuchaz.enigma.mapping.ConstructorEntry;
import cuchaz.enigma.mapping.Entry;
import cuchaz.enigma.mapping.FieldEntry;
import cuchaz.enigma.mapping.MethodEntry;
import cuchaz.enigma.mapping.SignatureUpdater;
import cuchaz.enigma.mapping.SignatureUpdater.ClassNameUpdater;

public class ClassIdentity {
    private ClassEntry m_classEntry;
    private SidedClassNamer m_namer;
    private Multiset<String> m_fields;
    private Multiset<String> m_methods;
    private Multiset<String> m_constructors;
    private String m_staticInitializer;
    private String m_extends;
    private Multiset<String> m_implements;
    private Multiset<String> m_implementations;
    private Multiset<String> m_references;

    public ClassIdentity(CtClass c, SidedClassNamer namer, JarIndex index, boolean useReferences) {
	m_namer = namer;

	// stuff from the bytecode

	m_classEntry = new ClassEntry(Descriptor.toJvmName(c.getName()));
	m_fields = HashMultiset.create();
	for (CtField field : c.getDeclaredFields()) {
	    m_fields.add(scrubSignature(field.getSignature()));
	}
	m_methods = HashMultiset.create();
	for (CtMethod method : c.getDeclaredMethods()) {
	    m_methods.add(scrubSignature(method.getSignature()) + "0x" + getBehaviorSignature(method));
	}
	m_constructors = HashMultiset.create();
	for (CtConstructor constructor : c.getDeclaredConstructors()) {
	    m_constructors.add(scrubSignature(constructor.getSignature()) + "0x" + getBehaviorSignature(constructor));
	}
	m_staticInitializer = "";
	if (c.getClassInitializer() != null) {
	    m_staticInitializer = getBehaviorSignature(c.getClassInitializer());
	}
	m_extends = "";
	if (c.getClassFile().getSuperclass() != null) {
	    m_extends = scrubClassName(c.getClassFile().getSuperclass());
	}
	m_implements = HashMultiset.create();
	for (String interfaceName : c.getClassFile().getInterfaces()) {
	    m_implements.add(scrubClassName(interfaceName));
	}

	// stuff from the jar index

	m_implementations = HashMultiset.create();
	ClassImplementationsTreeNode implementationsNode = index.getClassImplementations(null, m_classEntry);
	if (implementationsNode != null) {
	    @SuppressWarnings("unchecked")
	    Enumeration<ClassImplementationsTreeNode> implementations = implementationsNode.children();
	    while (implementations.hasMoreElements()) {
		ClassImplementationsTreeNode node = implementations.nextElement();
		m_implementations.add(scrubClassName(node.getClassEntry().getName()));
	    }
	}

	m_references = HashMultiset.create();
	if (useReferences) {
	    for (CtField field : c.getDeclaredFields()) {
		FieldEntry fieldEntry = new FieldEntry(m_classEntry, field.getName());
		for (EntryReference<FieldEntry, BehaviorEntry> reference : index.getFieldReferences(fieldEntry)) {
		    addReference(reference);
		}
	    }
	    for (CtMethod method : c.getDeclaredMethods()) {
		MethodEntry methodEntry = new MethodEntry(m_classEntry, method.getName(), method.getSignature());
		for (EntryReference<BehaviorEntry, BehaviorEntry> reference : index.getBehaviorReferences(methodEntry)) {
		    addReference(reference);
		}
	    }
	    for (CtConstructor constructor : c.getDeclaredConstructors()) {
		ConstructorEntry constructorEntry = new ConstructorEntry(m_classEntry, constructor.getSignature());
		for (EntryReference<BehaviorEntry, BehaviorEntry> reference : index
			.getBehaviorReferences(constructorEntry)) {
		    addReference(reference);
		}
	    }
	}
    }

    private void addReference(EntryReference<? extends Entry, BehaviorEntry> reference) {
	if (reference.context.getSignature() != null) {
	    m_references.add(String.format("%s_%s", scrubClassName(reference.context.getClassName()),
		    scrubSignature(reference.context.getSignature())));
	} else {
	    m_references.add(String.format("%s_<clinit>", scrubClassName(reference.context.getClassName())));
	}
    }

    public ClassEntry getClassEntry() {
	return m_classEntry;
    }

    @Override
    public String toString() {
	StringBuilder buf = new StringBuilder();
	buf.append("class: ");
	buf.append(m_classEntry.getName());
	buf.append(" ");
	buf.append(hashCode());
	buf.append("\n");
	for (String field : m_fields) {
	    buf.append("\tfield ");
	    buf.append(field);
	    buf.append("\n");
	}
	for (String method : m_methods) {
	    buf.append("\tmethod ");
	    buf.append(method);
	    buf.append("\n");
	}
	for (String constructor : m_constructors) {
	    buf.append("\tconstructor ");
	    buf.append(constructor);
	    buf.append("\n");
	}
	if (m_staticInitializer.length() > 0) {
	    buf.append("\tinitializer ");
	    buf.append(m_staticInitializer);
	    buf.append("\n");
	}
	if (m_extends.length() > 0) {
	    buf.append("\textends ");
	    buf.append(m_extends);
	    buf.append("\n");
	}
	for (String interfaceName : m_implements) {
	    buf.append("\timplements ");
	    buf.append(interfaceName);
	    buf.append("\n");
	}
	for (String implementation : m_implementations) {
	    buf.append("\timplemented by ");
	    buf.append(implementation);
	    buf.append("\n");
	}
	for (String reference : m_references) {
	    buf.append("\treference ");
	    buf.append(reference);
	    buf.append("\n");
	}
	return buf.toString();
    }

    private String scrubClassName(String className) {
	return scrubSignature("L" + Descriptor.toJvmName(className) + ";");
    }

    private String scrubSignature(String signature) {
	return SignatureUpdater.update(signature, new ClassNameUpdater() {
	    private Map<String, String> m_classNames = Maps.newHashMap();

	    @Override
	    public String update(String className) {
		// classes not in the none package can be passed through
		ClassEntry classEntry = new ClassEntry(className);
		if (!classEntry.getPackageName().equals(Constants.NonePackage)) {
		    return className;
		}

		// is this class ourself?
		if (className.equals(m_classEntry.getName())) {
		    return "CSelf";
		}

		// try the namer
		if (m_namer != null) {
		    String newName = m_namer.getName(className);
		    if (newName != null) {
			return newName;
		    }
		}

		// otherwise, use local naming
		if (!m_classNames.containsKey(className)) {
		    m_classNames.put(className, getNewClassName());
		}
		return m_classNames.get(className);
	    }

	    private String getNewClassName() {
		return String.format("C%03d", m_classNames.size());
	    }
	});
    }

    private boolean isClassMatchedUniquely(String className) {
	return m_namer != null && m_namer.getName(Descriptor.toJvmName(className)) != null;
    }

    private String getBehaviorSignature(CtBehavior behavior) {
	try {
	    // does this method have an implementation?
	    if (behavior.getMethodInfo().getCodeAttribute() == null) {
		return "(none)";
	    }

	    // compute the hash from the opcodes
	    ConstPool constants = behavior.getMethodInfo().getConstPool();
	    final MessageDigest digest = MessageDigest.getInstance("MD5");
	    CodeIterator iter = behavior.getMethodInfo().getCodeAttribute().iterator();
	    while (iter.hasNext()) {
		int pos = iter.next();

		// update the hash with the opcode
		int opcode = iter.byteAt(pos);
		digest.update((byte) opcode);

		switch (opcode) {
		case Opcode.LDC: {
		    int constIndex = iter.byteAt(pos + 1);
		    updateHashWithConstant(digest, constants, constIndex);
		}
		    break;

		case Opcode.LDC_W:
		case Opcode.LDC2_W: {
		    int constIndex = (iter.byteAt(pos + 1) << 8) | iter.byteAt(pos + 2);
		    updateHashWithConstant(digest, constants, constIndex);
		}
		    break;
		}
	    }

	    // update hash with method and field accesses
	    behavior.instrument(new ExprEditor() {
		@Override
		public void edit(MethodCall call) {
		    updateHashWithString(digest, scrubClassName(call.getClassName()));
		    updateHashWithString(digest, scrubSignature(call.getSignature()));
		    if (isClassMatchedUniquely(call.getClassName())) {
			updateHashWithString(digest, call.getMethodName());
		    }
		}

		@Override
		public void edit(FieldAccess access) {
		    updateHashWithString(digest, scrubClassName(access.getClassName()));
		    updateHashWithString(digest, scrubSignature(access.getSignature()));
		    if (isClassMatchedUniquely(access.getClassName())) {
			updateHashWithString(digest, access.getFieldName());
		    }
		}

		@Override
		public void edit(ConstructorCall call) {
		    updateHashWithString(digest, scrubClassName(call.getClassName()));
		    updateHashWithString(digest, scrubSignature(call.getSignature()));
		}

		@Override
		public void edit(NewExpr expr) {
		    updateHashWithString(digest, scrubClassName(expr.getClassName()));
		}
	    });

	    // convert the hash to a hex string
	    return toHex(digest.digest());
	} catch (BadBytecode | NoSuchAlgorithmException | CannotCompileException ex) {
	    throw new Error(ex);
	}
    }

    private void updateHashWithConstant(MessageDigest digest, ConstPool constants, int index) {
	ConstPoolEditor editor = new ConstPoolEditor(constants);
	ConstInfoAccessor item = editor.getItem(index);
	if (item.getType() == InfoType.StringInfo) {
	    updateHashWithString(digest, constants.getStringInfo(index));
	}
	// TODO: other constants
    }

    private void updateHashWithString(MessageDigest digest, String val) {
	try {
	    digest.update(val.getBytes("UTF8"));
	} catch (UnsupportedEncodingException ex) {
	    throw new Error(ex);
	}
    }

    private String toHex(byte[] bytes) {
	// function taken from:
	// http://stackoverflow.com/questions/9655181/convert-from-byte-array-to-hex-string-in-java
	final char[] hexArray = "0123456789ABCDEF".toCharArray();
	char[] hexChars = new char[bytes.length * 2];
	for (int j = 0; j < bytes.length; j++) {
	    int v = bytes[j] & 0xFF;
	    hexChars[j * 2] = hexArray[v >>> 4];
	    hexChars[j * 2 + 1] = hexArray[v & 0x0F];
	}
	return new String(hexChars);
    }

    @Override
    public boolean equals(Object other) {
	if (other instanceof ClassIdentity) {
	    return equals((ClassIdentity) other);
	}
	return false;
    }

    public boolean equals(ClassIdentity other) {
	return m_fields.equals(other.m_fields) && m_methods.equals(other.m_methods)
		&& m_constructors.equals(other.m_constructors) && m_staticInitializer.equals(other.m_staticInitializer)
		&& m_extends.equals(other.m_extends) && m_implements.equals(other.m_implements)
		&& m_implementations.equals(other.m_implementations) && m_references.equals(other.m_references);
    }

    @Override
    public int hashCode() {
	List<Object> objs = Lists.newArrayList();
	objs.addAll(m_fields);
	objs.addAll(m_methods);
	objs.addAll(m_constructors);
	objs.add(m_staticInitializer);
	objs.add(m_extends);
	objs.addAll(m_implements);
	objs.addAll(m_implementations);
	objs.addAll(m_references);
	return Util.combineHashesOrdered(objs);
    }

    public int getMatchScore(ClassIdentity other) {
	return getNumMatches(m_fields, other.m_fields) + getNumMatches(m_methods, other.m_methods)
		+ getNumMatches(m_constructors, other.m_constructors);
    }

    public int getMaxMatchScore() {
	return m_fields.size() + m_methods.size() + m_constructors.size();
    }

    public boolean matches(CtClass c) {
	// just compare declaration counts
	return m_fields.size() == c.getDeclaredFields().length && m_methods.size() == c.getDeclaredMethods().length
		&& m_constructors.size() == c.getDeclaredConstructors().length;
    }

    private int getNumMatches(Multiset<String> a, Multiset<String> b) {
	int numMatches = 0;
	for (String val : a) {
	    if (b.contains(val)) {
		numMatches++;
	    }
	}
	return numMatches;
    }
}
