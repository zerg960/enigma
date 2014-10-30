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
package cuchaz.enigma.analysis;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

import javassist.CannotCompileException;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.Descriptor;
import javassist.bytecode.FieldInfo;
import javassist.expr.ConstructorCall;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import javassist.expr.MethodCall;
import javassist.expr.NewExpr;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import cuchaz.enigma.Constants;
import cuchaz.enigma.bytecode.ClassRenamer;
import cuchaz.enigma.mapping.ArgumentEntry;
import cuchaz.enigma.mapping.BehaviorEntry;
import cuchaz.enigma.mapping.BehaviorEntryFactory;
import cuchaz.enigma.mapping.ClassEntry;
import cuchaz.enigma.mapping.ConstructorEntry;
import cuchaz.enigma.mapping.Entry;
import cuchaz.enigma.mapping.FieldEntry;
import cuchaz.enigma.mapping.MethodEntry;
import cuchaz.enigma.mapping.SignatureUpdater;
import cuchaz.enigma.mapping.Translator;

public class JarIndex {
    private Set<ClassEntry> m_obfClassEntries;
    private TranslationIndex m_translationIndex;
    private Multimap<String, String> m_interfaces;
    private Map<Entry, Access> m_access;
    private Map<FieldEntry, ClassEntry> m_fieldClasses;
    private Multimap<String, MethodEntry> m_methodImplementations;
    private Multimap<BehaviorEntry, EntryReference<BehaviorEntry, BehaviorEntry>> m_behaviorReferences;
    private Multimap<FieldEntry, EntryReference<FieldEntry, BehaviorEntry>> m_fieldReferences;
    private Multimap<String, String> m_innerClasses;
    private Map<String, String> m_outerClasses;
    private Map<String, BehaviorEntry> m_anonymousClasses;
    private Map<MethodEntry, MethodEntry> m_bridgeMethods;

    public JarIndex() {
	m_obfClassEntries = Sets.newHashSet();
	m_translationIndex = new TranslationIndex();
	m_interfaces = HashMultimap.create();
	m_access = Maps.newHashMap();
	m_fieldClasses = Maps.newHashMap();
	m_methodImplementations = HashMultimap.create();
	m_behaviorReferences = HashMultimap.create();
	m_fieldReferences = HashMultimap.create();
	m_innerClasses = HashMultimap.create();
	m_outerClasses = Maps.newHashMap();
	m_anonymousClasses = Maps.newHashMap();
	m_bridgeMethods = Maps.newHashMap();
    }

    public void indexJar(JarFile jar, boolean buildInnerClasses) {
	// step 1: read the class names
	for (ClassEntry classEntry : JarClassIterator.getClassEntries(jar)) {
	    if (classEntry.isInDefaultPackage()) {
		// move out of default package
		classEntry = new ClassEntry(Constants.NonePackage + "/" + classEntry.getName());
	    }
	    m_obfClassEntries.add(classEntry);
	}

	// step 2: index field/method/constructor access
	for (CtClass c : JarClassIterator.classes(jar)) {
	    ClassRenamer.moveAllClassesOutOfDefaultPackage(c, Constants.NonePackage);
	    ClassEntry classEntry = new ClassEntry(Descriptor.toJvmName(c.getName()));
	    for (CtField field : c.getDeclaredFields()) {
		FieldEntry fieldEntry = new FieldEntry(classEntry, field.getName());
		m_access.put(fieldEntry, Access.get(field));
	    }
	    for (CtMethod method : c.getDeclaredMethods()) {
		MethodEntry methodEntry = new MethodEntry(classEntry, method.getName(), method.getSignature());
		m_access.put(methodEntry, Access.get(method));
	    }
	    for (CtConstructor constructor : c.getDeclaredConstructors()) {
		ConstructorEntry constructorEntry = new ConstructorEntry(classEntry, constructor.getSignature());
		m_access.put(constructorEntry, Access.get(constructor));
	    }
	}

	// step 3: index extends, implements, fields, and methods
	for (CtClass c : JarClassIterator.classes(jar)) {
	    ClassRenamer.moveAllClassesOutOfDefaultPackage(c, Constants.NonePackage);
	    String className = Descriptor.toJvmName(c.getName());
	    m_translationIndex.addSuperclass(className, Descriptor.toJvmName(c.getClassFile().getSuperclass()));
	    for (String interfaceName : c.getClassFile().getInterfaces()) {
		className = Descriptor.toJvmName(className);
		interfaceName = Descriptor.toJvmName(interfaceName);
		if (className.equals(interfaceName)) {
		    throw new IllegalArgumentException("Class cannot be its own interface! " + className);
		}
		m_interfaces.put(className, interfaceName);
	    }
	    for (CtField field : c.getDeclaredFields()) {
		indexField(field);
	    }
	    for (CtBehavior behavior : c.getDeclaredBehaviors()) {
		indexBehavior(behavior);
	    }
	}

	// step 4: index field, method, constructor references
	for (CtClass c : JarClassIterator.classes(jar)) {
	    ClassRenamer.moveAllClassesOutOfDefaultPackage(c, Constants.NonePackage);
	    for (CtBehavior behavior : c.getDeclaredBehaviors()) {
		indexBehaviorReferences(behavior);
	    }
	}

	if (buildInnerClasses) {
	    // step 5: index inner classes and anonymous classes
	    for (CtClass c : JarClassIterator.classes(jar)) {
		ClassRenamer.moveAllClassesOutOfDefaultPackage(c, Constants.NonePackage);
		String outerClassName = findOuterClass(c);
		if (outerClassName != null) {
		    String innerClassName = c.getSimpleName();
		    m_innerClasses.put(outerClassName, innerClassName);
		    boolean innerWasAdded = m_outerClasses.put(innerClassName, outerClassName) == null;
		    assert (innerWasAdded);

		    BehaviorEntry enclosingBehavior = isAnonymousClass(c, outerClassName);
		    if (enclosingBehavior != null) {
			m_anonymousClasses.put(innerClassName, enclosingBehavior);

			// DEBUG
			// System.out.println( "ANONYMOUS: " + outerClassName +
			// "$" + innerClassName );
		    } else {
			// DEBUG
			// System.out.println( "INNER: " + outerClassName + "$"
			// + innerClassName );
		    }
		}
	    }

	    // step 6: update other indices with inner class info
	    Map<String, String> renames = Maps.newHashMap();
	    for (Map.Entry<String, String> entry : m_outerClasses.entrySet()) {
		renames.put(Constants.NonePackage + "/" + entry.getKey(), entry.getValue() + "$" + entry.getKey());
	    }
	    EntryRenamer.renameClassesInSet(renames, m_obfClassEntries);
	    m_translationIndex.renameClasses(renames);
	    EntryRenamer.renameClassesInMultimap(renames, m_interfaces);
	    EntryRenamer.renameClassesInMultimap(renames, m_methodImplementations);
	    EntryRenamer.renameClassesInMultimap(renames, m_behaviorReferences);
	    EntryRenamer.renameClassesInMultimap(renames, m_fieldReferences);
	    EntryRenamer.renameClassesInMap(renames, m_bridgeMethods);
	    EntryRenamer.renameClassesInMap(renames, m_access);
	}

	// step 6: update other indices with bridge method info
	EntryRenamer.renameMethodsInMultimap(m_bridgeMethods, m_methodImplementations);
	EntryRenamer.renameMethodsInMultimap(m_bridgeMethods, m_behaviorReferences);
	EntryRenamer.renameMethodsInMultimap(m_bridgeMethods, m_fieldReferences);
	EntryRenamer.renameMethodsInMap(m_bridgeMethods, m_access);
    }

    private void indexField(CtField field) {
	// get the field entry
	String className = Descriptor.toJvmName(field.getDeclaringClass().getName());
	FieldEntry fieldEntry = new FieldEntry(new ClassEntry(className), field.getName());

	m_translationIndex.addField(className, field.getName());

	// is the field a class type?
	if (field.getSignature().startsWith("L")) {
	    ClassEntry fieldTypeEntry = new ClassEntry(field.getSignature().substring(1,
		    field.getSignature().length() - 1));
	    m_fieldClasses.put(fieldEntry, fieldTypeEntry);
	}
    }

    private void indexBehavior(CtBehavior behavior) {
	// get the behavior entry
	final BehaviorEntry behaviorEntry = BehaviorEntryFactory.create(behavior);
	if (behaviorEntry instanceof MethodEntry) {
	    MethodEntry methodEntry = (MethodEntry) behaviorEntry;

	    // index implementation
	    m_methodImplementations.put(behaviorEntry.getClassName(), methodEntry);

	    // look for bridge methods
	    CtMethod bridgedMethod = getBridgedMethod((CtMethod) behavior);
	    if (bridgedMethod != null) {
		MethodEntry bridgedMethodEntry = new MethodEntry(behaviorEntry.getClassEntry(),
			bridgedMethod.getName(), bridgedMethod.getSignature());
		m_bridgeMethods.put(bridgedMethodEntry, methodEntry);
	    }
	}
	// looks like we don't care about constructors here
    }

    private void indexBehaviorReferences(CtBehavior behavior) {
	// index method calls
	final BehaviorEntry behaviorEntry = BehaviorEntryFactory.create(behavior);
	try {
	    behavior.instrument(new ExprEditor() {
		@Override
		public void edit(MethodCall call) {
		    String className = Descriptor.toJvmName(call.getClassName());
		    MethodEntry calledMethodEntry = new MethodEntry(new ClassEntry(className), call.getMethodName(),
			    call.getSignature());
		    ClassEntry resolvedClassEntry = resolveEntryClass(calledMethodEntry);
		    if (resolvedClassEntry != null && !resolvedClassEntry.equals(calledMethodEntry.getClassEntry())) {
			calledMethodEntry = new MethodEntry(resolvedClassEntry, call.getMethodName(), call
				.getSignature());
		    }
		    EntryReference<BehaviorEntry, BehaviorEntry> reference = new EntryReference<BehaviorEntry, BehaviorEntry>(
			    calledMethodEntry, call.getMethodName(), behaviorEntry);
		    m_behaviorReferences.put(calledMethodEntry, reference);
		}

		@Override
		public void edit(FieldAccess call) {
		    String className = Descriptor.toJvmName(call.getClassName());
		    FieldEntry calledFieldEntry = new FieldEntry(new ClassEntry(className), call.getFieldName());
		    ClassEntry resolvedClassEntry = resolveEntryClass(calledFieldEntry);
		    if (resolvedClassEntry != null && !resolvedClassEntry.equals(calledFieldEntry.getClassEntry())) {
			calledFieldEntry = new FieldEntry(resolvedClassEntry, call.getFieldName());
		    }
		    EntryReference<FieldEntry, BehaviorEntry> reference = new EntryReference<FieldEntry, BehaviorEntry>(
			    calledFieldEntry, call.getFieldName(), behaviorEntry);
		    m_fieldReferences.put(calledFieldEntry, reference);
		}

		@Override
		public void edit(ConstructorCall call) {
		    String className = Descriptor.toJvmName(call.getClassName());
		    ConstructorEntry calledConstructorEntry = new ConstructorEntry(new ClassEntry(className), call
			    .getSignature());
		    EntryReference<BehaviorEntry, BehaviorEntry> reference = new EntryReference<BehaviorEntry, BehaviorEntry>(
			    calledConstructorEntry, call.getMethodName(), behaviorEntry);
		    m_behaviorReferences.put(calledConstructorEntry, reference);
		}

		@Override
		public void edit(NewExpr call) {
		    String className = Descriptor.toJvmName(call.getClassName());
		    ConstructorEntry calledConstructorEntry = new ConstructorEntry(new ClassEntry(className), call
			    .getSignature());
		    EntryReference<BehaviorEntry, BehaviorEntry> reference = new EntryReference<BehaviorEntry, BehaviorEntry>(
			    calledConstructorEntry, call.getClassName(), behaviorEntry);
		    m_behaviorReferences.put(calledConstructorEntry, reference);
		}
	    });
	} catch (CannotCompileException ex) {
	    throw new Error(ex);
	}
    }

    public ClassEntry resolveEntryClass(Entry obfEntry) {
	// this entry could refer to a method on a class where the method is not
	// actually implemented
	// travel up the inheritance tree to find the closest implementation
	while (!containsObfEntry(obfEntry)) {
	    // is there a parent class?
	    String superclassName = m_translationIndex.getSuperclassName(obfEntry.getClassName());
	    if (superclassName == null) {
		// this is probably a method from a class in a library
		// we can't trace the implementation up any higher unless we
		// index the library
		return null;
	    }

	    // move up to the parent class
	    obfEntry = obfEntry.cloneToNewClass(new ClassEntry(superclassName));
	}
	return obfEntry.getClassEntry();
    }

    private CtMethod getBridgedMethod(CtMethod method) {
	// bridge methods just call another method, cast it to the return type,
	// and return the result
	// let's see if we can detect this scenario

	// skip non-synthetic methods
	if ((method.getModifiers() & AccessFlag.SYNTHETIC) == 0) {
	    return null;
	}

	// get all the called methods
	final List<MethodCall> methodCalls = Lists.newArrayList();
	try {
	    method.instrument(new ExprEditor() {
		@Override
		public void edit(MethodCall call) {
		    methodCalls.add(call);
		}
	    });
	} catch (CannotCompileException ex) {
	    // this is stupid... we're not even compiling anything
	    throw new Error(ex);
	}

	// is there just one?
	if (methodCalls.size() != 1) {
	    return null;
	}
	MethodCall call = methodCalls.get(0);

	try {
	    // we have a bridge method!
	    return call.getMethod();
	} catch (NotFoundException ex) {
	    // can't find the type? not a bridge method
	    return null;
	}
    }

    private String findOuterClass(CtClass c) {
	// inner classes:
	// have constructors that can (illegally) set synthetic fields
	// the outer class is the only class that calls constructors

	// use the synthetic fields to find the synthetic constructors
	for (CtConstructor constructor : c.getDeclaredConstructors()) {
	    Set<String> syntheticFieldTypes = Sets.newHashSet();
	    if (!isIllegalConstructor(syntheticFieldTypes, constructor)) {
		continue;
	    }

	    ClassEntry classEntry = new ClassEntry(Descriptor.toJvmName(c.getName()));
	    ConstructorEntry constructorEntry = new ConstructorEntry(classEntry, constructor.getMethodInfo()
		    .getDescriptor());

	    // gather the classes from the illegally-set synthetic fields
	    Set<ClassEntry> illegallySetClasses = Sets.newHashSet();
	    for (String type : syntheticFieldTypes) {
		if (type.startsWith("L")) {
		    ClassEntry outerClassEntry = new ClassEntry(type.substring(1, type.length() - 1));
		    if (isSaneOuterClass(outerClassEntry, classEntry)) {
			illegallySetClasses.add(outerClassEntry);
		    }
		}
	    }

	    // who calls this constructor?
	    Set<ClassEntry> callerClasses = Sets.newHashSet();
	    for (EntryReference<BehaviorEntry, BehaviorEntry> reference : getBehaviorReferences(constructorEntry)) {
		// make sure it's not a call to super
		if (reference.entry instanceof ConstructorEntry && reference.context instanceof ConstructorEntry) {
		    // is the entry a superclass of the context?
		    String calledClassName = reference.entry.getClassName();
		    String callerSuperclassName = m_translationIndex
			    .getSuperclassName(reference.context.getClassName());
		    if (callerSuperclassName != null && callerSuperclassName.equals(calledClassName)) {
			// it's a super call, skip
			continue;
		    }
		}

		if (isSaneOuterClass(reference.context.getClassEntry(), classEntry)) {
		    callerClasses.add(reference.context.getClassEntry());
		}
	    }

	    // do we have an answer yet?
	    if (callerClasses.isEmpty()) {
		if (illegallySetClasses.size() == 1) {
		    return illegallySetClasses.iterator().next().getName();
		} else {
		    System.out
			    .println(String
				    .format("WARNING: Unable to find outer class for %s. No caller and no illegally set field classes.",
					    classEntry));
		}
	    } else {
		if (callerClasses.size() == 1) {
		    return callerClasses.iterator().next().getName();
		} else {
		    // multiple callers, do the illegally set classes narrow it
		    // down?
		    Set<ClassEntry> intersection = Sets.newHashSet(callerClasses);
		    intersection.retainAll(illegallySetClasses);
		    if (intersection.size() == 1) {
			return intersection.iterator().next().getName();
		    } else {
			System.out.println(String.format(
				"WARNING: Unable to choose outer class for %s among options: %s", classEntry,
				callerClasses));
		    }
		}
	    }
	}

	return null;
    }

    private boolean isSaneOuterClass(ClassEntry outerClassEntry, ClassEntry innerClassEntry) {
	// clearly this would be silly
	if (outerClassEntry.equals(innerClassEntry)) {
	    return false;
	}

	// is the outer class in the jar?
	if (!m_obfClassEntries.contains(outerClassEntry)) {
	    return false;
	}

	return true;
    }

    @SuppressWarnings("unchecked")
    private boolean isIllegalConstructor(Set<String> syntheticFieldTypes, CtConstructor constructor) {
	// illegal constructors only set synthetic member fields, then call
	// super()
	String className = constructor.getDeclaringClass().getName();

	// collect all the field accesses, constructor calls, and method calls
	final List<FieldAccess> illegalFieldWrites = Lists.newArrayList();
	final List<ConstructorCall> constructorCalls = Lists.newArrayList();
	try {
	    constructor.instrument(new ExprEditor() {
		@Override
		public void edit(FieldAccess fieldAccess) {
		    if (fieldAccess.isWriter() && constructorCalls.isEmpty()) {
			illegalFieldWrites.add(fieldAccess);
		    }
		}

		@Override
		public void edit(ConstructorCall constructorCall) {
		    constructorCalls.add(constructorCall);
		}
	    });
	} catch (CannotCompileException ex) {
	    // we're not compiling anything... this is stupid
	    throw new Error(ex);
	}

	// are there any illegal field writes?
	if (illegalFieldWrites.isEmpty()) {
	    return false;
	}

	// are all the writes to synthetic fields?
	for (FieldAccess fieldWrite : illegalFieldWrites) {
	    // all illegal writes have to be to the local class
	    if (!fieldWrite.getClassName().equals(className)) {
		System.err.println(String.format("WARNING: illegal write to non-member field %s.%s",
			fieldWrite.getClassName(), fieldWrite.getFieldName()));
		return false;
	    }

	    // find the field
	    FieldInfo fieldInfo = null;
	    for (FieldInfo info : (List<FieldInfo>) constructor.getDeclaringClass().getClassFile().getFields()) {
		if (info.getName().equals(fieldWrite.getFieldName())
			&& info.getDescriptor().equals(fieldWrite.getSignature())) {
		    fieldInfo = info;
		    break;
		}
	    }
	    if (fieldInfo == null) {
		// field is in a superclass or something, can't be a local
		// synthetic member
		return false;
	    }

	    // is this field synthetic?
	    boolean isSynthetic = (fieldInfo.getAccessFlags() & AccessFlag.SYNTHETIC) != 0;
	    if (isSynthetic) {
		syntheticFieldTypes.add(fieldInfo.getDescriptor());
	    } else {
		System.err.println(String.format("WARNING: illegal write to non synthetic field %s %s.%s",
			fieldInfo.getDescriptor(), className, fieldInfo.getName()));
		return false;
	    }
	}

	// we passed all the tests!
	return true;
    }

    private BehaviorEntry isAnonymousClass(CtClass c, String outerClassName) {
	ClassEntry innerClassEntry = new ClassEntry(Descriptor.toJvmName(c.getName()));

	// anonymous classes:
	// can't be abstract
	// have only one constructor
	// it's called exactly once by the outer class
	// the type the instance is assigned to can't be this type

	// is abstract?
	if (Modifier.isAbstract(c.getModifiers())) {
	    return null;
	}

	// is there exactly one constructor?
	if (c.getDeclaredConstructors().length != 1) {
	    return null;
	}
	CtConstructor constructor = c.getDeclaredConstructors()[0];

	// is this constructor called exactly once?
	ConstructorEntry constructorEntry = new ConstructorEntry(innerClassEntry, constructor.getMethodInfo()
		.getDescriptor());
	Collection<EntryReference<BehaviorEntry, BehaviorEntry>> references = getBehaviorReferences(constructorEntry);
	if (references.size() != 1) {
	    return null;
	}

	// does the caller use this type?
	BehaviorEntry caller = references.iterator().next().context;
	for (FieldEntry fieldEntry : getReferencedFields(caller)) {
	    ClassEntry fieldClass = getFieldClass(fieldEntry);
	    if (fieldClass != null && fieldClass.equals(innerClassEntry)) {
		// caller references this type, so it can't be anonymous
		return null;
	    }
	}
	for (BehaviorEntry behaviorEntry : getReferencedBehaviors(caller)) {
	    // get the class types from the signature
	    for (String className : SignatureUpdater.getClasses(behaviorEntry.getSignature())) {
		if (className.equals(innerClassEntry.getName())) {
		    // caller references this type, so it can't be anonymous
		    return null;
		}
	    }
	}

	return caller;
    }

    public Set<ClassEntry> getObfClassEntries() {
	return m_obfClassEntries;
    }

    public TranslationIndex getTranslationIndex() {
	return m_translationIndex;
    }

    public Access getAccess(Entry entry) {
	return m_access.get(entry);
    }

    public ClassEntry getFieldClass(FieldEntry fieldEntry) {
	return m_fieldClasses.get(fieldEntry);
    }

    public ClassInheritanceTreeNode getClassInheritance(Translator deobfuscatingTranslator, ClassEntry obfClassEntry) {
	// get the root node
	List<String> ancestry = Lists.newArrayList();
	ancestry.add(obfClassEntry.getName());
	ancestry.addAll(m_translationIndex.getAncestry(obfClassEntry.getName()));
	ClassInheritanceTreeNode rootNode = new ClassInheritanceTreeNode(deobfuscatingTranslator, ancestry.get(ancestry
		.size() - 1));

	// expand all children recursively
	rootNode.load(m_translationIndex, true);

	return rootNode;
    }

    public ClassImplementationsTreeNode getClassImplementations(Translator deobfuscatingTranslator,
	    ClassEntry obfClassEntry) {
	// is this even an interface?
	if (isInterface(obfClassEntry.getClassName())) {
	    ClassImplementationsTreeNode node = new ClassImplementationsTreeNode(deobfuscatingTranslator, obfClassEntry);
	    node.load(this);
	    return node;
	}
	return null;
    }

    public MethodInheritanceTreeNode getMethodInheritance(Translator deobfuscatingTranslator, MethodEntry obfMethodEntry) {
	// travel to the ancestor implementation
	String baseImplementationClassName = obfMethodEntry.getClassName();
	for (String ancestorClassName : m_translationIndex.getAncestry(obfMethodEntry.getClassName())) {
	    MethodEntry ancestorMethodEntry = new MethodEntry(new ClassEntry(ancestorClassName),
		    obfMethodEntry.getName(), obfMethodEntry.getSignature());
	    if (containsObfBehavior(ancestorMethodEntry)) {
		baseImplementationClassName = ancestorClassName;
	    }
	}

	// make a root node at the base
	MethodEntry methodEntry = new MethodEntry(new ClassEntry(baseImplementationClassName),
		obfMethodEntry.getName(), obfMethodEntry.getSignature());
	MethodInheritanceTreeNode rootNode = new MethodInheritanceTreeNode(deobfuscatingTranslator, methodEntry,
		containsObfBehavior(methodEntry));

	// expand the full tree
	rootNode.load(this, true);

	return rootNode;
    }

    public MethodImplementationsTreeNode getMethodImplementations(Translator deobfuscatingTranslator,
	    MethodEntry obfMethodEntry) {
	MethodEntry interfaceMethodEntry;

	// is this method on an interface?
	if (isInterface(obfMethodEntry.getClassName())) {
	    interfaceMethodEntry = obfMethodEntry;
	} else {
	    // get the interface class
	    List<MethodEntry> methodInterfaces = Lists.newArrayList();
	    for (String interfaceName : getInterfaces(obfMethodEntry.getClassName())) {
		// is this method defined in this interface?
		MethodEntry methodInterface = new MethodEntry(new ClassEntry(interfaceName), obfMethodEntry.getName(),
			obfMethodEntry.getSignature());
		if (containsObfBehavior(methodInterface)) {
		    methodInterfaces.add(methodInterface);
		}
	    }
	    if (methodInterfaces.isEmpty()) {
		return null;
	    }
	    if (methodInterfaces.size() > 1) {
		throw new Error("Too many interfaces define this method! This is not yet supported by Enigma!");
	    }
	    interfaceMethodEntry = methodInterfaces.get(0);
	}

	MethodImplementationsTreeNode rootNode = new MethodImplementationsTreeNode(deobfuscatingTranslator,
		interfaceMethodEntry);
	rootNode.load(this);
	return rootNode;
    }

    public Set<MethodEntry> getRelatedMethodImplementations(MethodEntry obfMethodEntry) {
	Set<MethodEntry> methodEntries = Sets.newHashSet();
	getRelatedMethodImplementations(methodEntries, getMethodInheritance(null, obfMethodEntry));
	return methodEntries;
    }

    private void getRelatedMethodImplementations(Set<MethodEntry> methodEntries, MethodInheritanceTreeNode node) {
	MethodEntry methodEntry = node.getMethodEntry();
	if (containsObfBehavior(methodEntry)) {
	    // collect the entry
	    methodEntries.add(methodEntry);
	}

	// look at interface methods too
	MethodImplementationsTreeNode implementations = getMethodImplementations(null, methodEntry);
	if (implementations != null) {
	    getRelatedMethodImplementations(methodEntries, implementations);
	}

	// recurse
	for (int i = 0; i < node.getChildCount(); i++) {
	    getRelatedMethodImplementations(methodEntries, (MethodInheritanceTreeNode) node.getChildAt(i));
	}
    }

    private void getRelatedMethodImplementations(Set<MethodEntry> methodEntries, MethodImplementationsTreeNode node) {
	MethodEntry methodEntry = node.getMethodEntry();
	if (containsObfBehavior(methodEntry)) {
	    // collect the entry
	    methodEntries.add(methodEntry);
	}

	// recurse
	for (int i = 0; i < node.getChildCount(); i++) {
	    getRelatedMethodImplementations(methodEntries, (MethodImplementationsTreeNode) node.getChildAt(i));
	}
    }

    public Collection<EntryReference<FieldEntry, BehaviorEntry>> getFieldReferences(FieldEntry fieldEntry) {
	return m_fieldReferences.get(fieldEntry);
    }

    public Collection<FieldEntry> getReferencedFields(BehaviorEntry behaviorEntry) {
	// linear search is fast enough for now
	Set<FieldEntry> fieldEntries = Sets.newHashSet();
	for (EntryReference<FieldEntry, BehaviorEntry> reference : m_fieldReferences.values()) {
	    if (reference.context == behaviorEntry) {
		fieldEntries.add(reference.entry);
	    }
	}
	return fieldEntries;
    }

    public Collection<EntryReference<BehaviorEntry, BehaviorEntry>> getBehaviorReferences(BehaviorEntry behaviorEntry) {
	return m_behaviorReferences.get(behaviorEntry);
    }

    public Collection<BehaviorEntry> getReferencedBehaviors(BehaviorEntry behaviorEntry) {
	// linear search is fast enough for now
	Set<BehaviorEntry> behaviorEntries = Sets.newHashSet();
	for (EntryReference<BehaviorEntry, BehaviorEntry> reference : m_behaviorReferences.values()) {
	    if (reference.context == behaviorEntry) {
		behaviorEntries.add(reference.entry);
	    }
	}
	return behaviorEntries;
    }

    public Collection<String> getInnerClasses(String obfOuterClassName) {
	return m_innerClasses.get(obfOuterClassName);
    }

    public String getOuterClass(String obfInnerClassName) {
	// make sure we use the right name
	if (new ClassEntry(obfInnerClassName).getPackageName() != null) {
	    throw new IllegalArgumentException("Don't reference obfuscated inner classes using packages: "
		    + obfInnerClassName);
	}
	return m_outerClasses.get(obfInnerClassName);
    }

    public boolean isAnonymousClass(String obfInnerClassName) {
	return m_anonymousClasses.containsKey(obfInnerClassName);
    }

    public BehaviorEntry getAnonymousClassCaller(String obfInnerClassName) {
	return m_anonymousClasses.get(obfInnerClassName);
    }

    public Set<String> getInterfaces(String className) {
	Set<String> interfaceNames = new HashSet<String>();
	interfaceNames.addAll(m_interfaces.get(className));
	for (String ancestor : m_translationIndex.getAncestry(className)) {
	    interfaceNames.addAll(m_interfaces.get(ancestor));
	}
	return interfaceNames;
    }

    public Set<String> getImplementingClasses(String targetInterfaceName) {
	// linear search is fast enough for now
	Set<String> classNames = Sets.newHashSet();
	for (Map.Entry<String, String> entry : m_interfaces.entries()) {
	    String className = entry.getKey();
	    String interfaceName = entry.getValue();
	    if (interfaceName.equals(targetInterfaceName)) {
		classNames.add(className);
		m_translationIndex.getSubclassNamesRecursively(classNames, className);
	    }
	}
	return classNames;
    }

    public boolean isInterface(String className) {
	return m_interfaces.containsValue(className);
    }

    public MethodEntry getBridgeMethod(MethodEntry methodEntry) {
	return m_bridgeMethods.get(methodEntry);
    }

    public boolean containsObfClass(ClassEntry obfClassEntry) {
	return m_obfClassEntries.contains(obfClassEntry);
    }

    public boolean containsObfField(FieldEntry obfFieldEntry) {
	return m_access.containsKey(obfFieldEntry);
    }

    public boolean containsObfBehavior(BehaviorEntry obfBehaviorEntry) {
	return m_access.containsKey(obfBehaviorEntry);
    }

    public boolean containsObfArgument(ArgumentEntry obfArgumentEntry) {
	// check the behavior
	if (!containsObfBehavior(obfArgumentEntry.getBehaviorEntry())) {
	    return false;
	}

	// check the argument
	if (obfArgumentEntry.getIndex() >= Descriptor.numOfParameters(obfArgumentEntry.getBehaviorEntry()
		.getSignature())) {
	    return false;
	}

	return true;
    }

    public boolean containsObfEntry(Entry obfEntry) {
	if (obfEntry instanceof ClassEntry) {
	    return containsObfClass((ClassEntry) obfEntry);
	} else if (obfEntry instanceof FieldEntry) {
	    return containsObfField((FieldEntry) obfEntry);
	} else if (obfEntry instanceof BehaviorEntry) {
	    return containsObfBehavior((BehaviorEntry) obfEntry);
	} else if (obfEntry instanceof ArgumentEntry) {
	    return containsObfArgument((ArgumentEntry) obfEntry);
	} else {
	    throw new Error("Entry type not supported: " + obfEntry.getClass().getName());
	}
    }
}
