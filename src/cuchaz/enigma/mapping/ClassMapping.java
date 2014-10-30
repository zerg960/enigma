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
package cuchaz.enigma.mapping;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;

import com.google.common.collect.Maps;

public class ClassMapping implements Serializable, Comparable<ClassMapping> {
    private static final long serialVersionUID = -5148491146902340107L;

    private String m_obfName;
    private String m_deobfName;
    private Map<String, ClassMapping> m_innerClassesByObf;
    private Map<String, ClassMapping> m_innerClassesByDeobf;
    private Map<String, FieldMapping> m_fieldsByObf;
    private Map<String, FieldMapping> m_fieldsByDeobf;
    private Map<String, MethodMapping> m_methodsByObf;
    private Map<String, MethodMapping> m_methodsByDeobf;

    public ClassMapping(String obfName) {
	this(obfName, null);
    }

    public ClassMapping(String obfName, String deobfName) {
	m_obfName = obfName;
	m_deobfName = NameValidator.validateClassName(deobfName, false);
	m_innerClassesByObf = Maps.newHashMap();
	m_innerClassesByDeobf = Maps.newHashMap();
	m_fieldsByObf = Maps.newHashMap();
	m_fieldsByDeobf = Maps.newHashMap();
	m_methodsByObf = Maps.newHashMap();
	m_methodsByDeobf = Maps.newHashMap();
    }

    public String getObfName() {
	return m_obfName;
    }

    public String getDeobfName() {
	return m_deobfName;
    }

    public void setDeobfName(String val) {
	m_deobfName = NameValidator.validateClassName(val, false);
    }

    // // INNER CLASSES ////////

    public Iterable<ClassMapping> innerClasses() {
	assert (m_innerClassesByObf.size() >= m_innerClassesByDeobf.size());
	return m_innerClassesByObf.values();
    }

    public void addInnerClassMapping(ClassMapping classMapping) {
	assert (isSimpleClassName(classMapping.getObfName()));
	boolean obfWasAdded = m_innerClassesByObf.put(classMapping.getObfName(), classMapping) == null;
	assert (obfWasAdded);
	if (classMapping.getDeobfName() != null) {
	    assert (isSimpleClassName(classMapping.getDeobfName()));
	    boolean deobfWasAdded = m_innerClassesByDeobf.put(classMapping.getDeobfName(), classMapping) == null;
	    assert (deobfWasAdded);
	}
    }

    public void removeInnerClassMapping(ClassMapping classMapping) {
	boolean obfWasRemoved = m_innerClassesByObf.remove(classMapping.getObfName()) != null;
	assert (obfWasRemoved);
	if (classMapping.getDeobfName() != null) {
	    boolean deobfWasRemoved = m_innerClassesByDeobf.remove(classMapping.getDeobfName()) != null;
	    assert (deobfWasRemoved);
	}
    }

    public ClassMapping getOrCreateInnerClass(String obfName) {
	assert (isSimpleClassName(obfName));
	ClassMapping classMapping = m_innerClassesByObf.get(obfName);
	if (classMapping == null) {
	    classMapping = new ClassMapping(obfName);
	    boolean wasAdded = m_innerClassesByObf.put(obfName, classMapping) == null;
	    assert (wasAdded);
	}
	return classMapping;
    }

    public ClassMapping getInnerClassByObf(String obfName) {
	assert (isSimpleClassName(obfName));
	return m_innerClassesByObf.get(obfName);
    }

    public ClassMapping getInnerClassByDeobf(String deobfName) {
	assert (isSimpleClassName(deobfName));
	return m_innerClassesByDeobf.get(deobfName);
    }

    public ClassMapping getInnerClassByDeobfThenObf(String name) {
	ClassMapping classMapping = getInnerClassByDeobf(name);
	if (classMapping == null) {
	    classMapping = getInnerClassByObf(name);
	}
	return classMapping;
    }

    public String getObfInnerClassName(String deobfName) {
	assert (isSimpleClassName(deobfName));
	ClassMapping classMapping = m_innerClassesByDeobf.get(deobfName);
	if (classMapping != null) {
	    return classMapping.getObfName();
	}
	return null;
    }

    public String getDeobfInnerClassName(String obfName) {
	assert (isSimpleClassName(obfName));
	ClassMapping classMapping = m_innerClassesByObf.get(obfName);
	if (classMapping != null) {
	    return classMapping.getDeobfName();
	}
	return null;
    }

    public void setInnerClassName(String obfName, String deobfName) {
	assert (isSimpleClassName(obfName));
	ClassMapping classMapping = getOrCreateInnerClass(obfName);
	if (classMapping.getDeobfName() != null) {
	    boolean wasRemoved = m_innerClassesByDeobf.remove(classMapping.getDeobfName()) != null;
	    assert (wasRemoved);
	}
	classMapping.setDeobfName(deobfName);
	if (deobfName != null) {
	    assert (isSimpleClassName(deobfName));
	    boolean wasAdded = m_innerClassesByDeobf.put(deobfName, classMapping) == null;
	    assert (wasAdded);
	}
    }

    // // FIELDS ////////

    public Iterable<FieldMapping> fields() {
	assert (m_fieldsByObf.size() == m_fieldsByDeobf.size());
	return m_fieldsByObf.values();
    }

    public boolean containsObfField(String obfName) {
	return m_fieldsByObf.containsKey(obfName);
    }

    public boolean containsDeobfField(String deobfName) {
	return m_fieldsByDeobf.containsKey(deobfName);
    }

    public void addFieldMapping(FieldMapping fieldMapping) {
	if (m_fieldsByObf.containsKey(fieldMapping.getObfName())) {
	    throw new Error("Already have mapping for " + m_obfName + "." + fieldMapping.getObfName());
	}
	if (m_fieldsByDeobf.containsKey(fieldMapping.getDeobfName())) {
	    throw new Error("Already have mapping for " + m_deobfName + "." + fieldMapping.getDeobfName());
	}
	boolean obfWasAdded = m_fieldsByObf.put(fieldMapping.getObfName(), fieldMapping) == null;
	assert (obfWasAdded);
	boolean deobfWasAdded = m_fieldsByDeobf.put(fieldMapping.getDeobfName(), fieldMapping) == null;
	assert (deobfWasAdded);
	assert (m_fieldsByObf.size() == m_fieldsByDeobf.size());
    }

    public void removeFieldMapping(FieldMapping fieldMapping) {
	boolean obfWasRemoved = m_fieldsByObf.remove(fieldMapping.getObfName()) != null;
	assert (obfWasRemoved);
	if (fieldMapping.getDeobfName() != null) {
	    boolean deobfWasRemoved = m_fieldsByDeobf.remove(fieldMapping.getDeobfName()) != null;
	    assert (deobfWasRemoved);
	}
    }

    public FieldMapping getFieldByObf(String obfName) {
	return m_fieldsByObf.get(obfName);
    }

    public FieldMapping getFieldByDeobf(String deobfName) {
	return m_fieldsByDeobf.get(deobfName);
    }

    public String getObfFieldName(String deobfName) {
	FieldMapping fieldMapping = m_fieldsByDeobf.get(deobfName);
	if (fieldMapping != null) {
	    return fieldMapping.getObfName();
	}
	return null;
    }

    public String getDeobfFieldName(String obfName) {
	FieldMapping fieldMapping = m_fieldsByObf.get(obfName);
	if (fieldMapping != null) {
	    return fieldMapping.getDeobfName();
	}
	return null;
    }

    public void setFieldName(String obfName, String deobfName) {
	FieldMapping fieldMapping = m_fieldsByObf.get(obfName);
	if (fieldMapping == null) {
	    fieldMapping = new FieldMapping(obfName, deobfName);
	    boolean obfWasAdded = m_fieldsByObf.put(obfName, fieldMapping) == null;
	    assert (obfWasAdded);
	} else {
	    boolean wasRemoved = m_fieldsByDeobf.remove(fieldMapping.getDeobfName()) != null;
	    assert (wasRemoved);
	}
	fieldMapping.setDeobfName(deobfName);
	if (deobfName != null) {
	    boolean wasAdded = m_fieldsByDeobf.put(deobfName, fieldMapping) == null;
	    assert (wasAdded);
	}
    }

    // // METHODS ////////

    public Iterable<MethodMapping> methods() {
	assert (m_methodsByObf.size() >= m_methodsByDeobf.size());
	return m_methodsByObf.values();
    }

    public boolean containsObfMethod(String obfName, String obfSignature) {
	return m_methodsByObf.containsKey(getMethodKey(obfName, obfSignature));
    }

    public boolean containsDeobfMethod(String deobfName, String deobfSignature) {
	return m_methodsByDeobf.containsKey(getMethodKey(deobfName, deobfSignature));
    }

    public void addMethodMapping(MethodMapping methodMapping) {
	String obfKey = getMethodKey(methodMapping.getObfName(), methodMapping.getObfSignature());
	if (m_methodsByObf.containsKey(obfKey)) {
	    throw new Error("Already have mapping for " + m_obfName + "." + obfKey);
	}
	boolean wasAdded = m_methodsByObf.put(obfKey, methodMapping) == null;
	assert (wasAdded);
	if (methodMapping.getDeobfName() != null) {
	    String deobfKey = getMethodKey(methodMapping.getDeobfName(), methodMapping.getObfSignature());
	    if (m_methodsByDeobf.containsKey(deobfKey)) {
		throw new Error("Already have mapping for " + m_deobfName + "." + deobfKey);
	    }
	    boolean deobfWasAdded = m_methodsByDeobf.put(deobfKey, methodMapping) == null;
	    assert (deobfWasAdded);
	}
	assert (m_methodsByObf.size() >= m_methodsByDeobf.size());
    }

    public void removeMethodMapping(MethodMapping methodMapping) {
	boolean obfWasRemoved = m_methodsByObf.remove(getMethodKey(methodMapping.getObfName(),
		methodMapping.getObfSignature())) != null;
	assert (obfWasRemoved);
	if (methodMapping.getDeobfName() != null) {
	    boolean deobfWasRemoved = m_methodsByDeobf.remove(getMethodKey(methodMapping.getDeobfName(),
		    methodMapping.getObfSignature())) != null;
	    assert (deobfWasRemoved);
	}
    }

    public MethodMapping getMethodByObf(String obfName, String signature) {
	return m_methodsByObf.get(getMethodKey(obfName, signature));
    }

    public MethodMapping getMethodByDeobf(String deobfName, String signature) {
	return m_methodsByDeobf.get(getMethodKey(deobfName, signature));
    }

    private String getMethodKey(String name, String signature) {
	if (name == null) {
	    throw new IllegalArgumentException("name cannot be null!");
	}
	if (signature == null) {
	    throw new IllegalArgumentException("signature cannot be null!");
	}
	return name + signature;
    }

    public void setMethodName(String obfName, String obfSignature, String deobfName) {
	MethodMapping methodMapping = m_methodsByObf.get(getMethodKey(obfName, obfSignature));
	if (methodMapping == null) {
	    methodMapping = createMethodMapping(obfName, obfSignature);
	} else if (methodMapping.getDeobfName() != null) {
	    boolean wasRemoved = m_methodsByDeobf.remove(getMethodKey(methodMapping.getDeobfName(),
		    methodMapping.getObfSignature())) != null;
	    assert (wasRemoved);
	}
	methodMapping.setDeobfName(deobfName);
	if (deobfName != null) {
	    boolean wasAdded = m_methodsByDeobf.put(getMethodKey(deobfName, obfSignature), methodMapping) == null;
	    assert (wasAdded);
	}
    }

    // // ARGUMENTS ////////

    public void setArgumentName(String obfMethodName, String obfMethodSignature, int argumentIndex, String argumentName) {
	MethodMapping methodMapping = m_methodsByObf.get(getMethodKey(obfMethodName, obfMethodSignature));
	if (methodMapping == null) {
	    methodMapping = createMethodMapping(obfMethodName, obfMethodSignature);
	}
	methodMapping.setArgumentName(argumentIndex, argumentName);
    }

    public void removeArgumentName(String obfMethodName, String obfMethodSignature, int argumentIndex) {
	m_methodsByObf.get(getMethodKey(obfMethodName, obfMethodSignature)).removeArgumentName(argumentIndex);
    }

    private MethodMapping createMethodMapping(String obfName, String obfSignature) {
	MethodMapping methodMapping = new MethodMapping(obfName, obfSignature);
	boolean wasAdded = m_methodsByObf.put(getMethodKey(obfName, obfSignature), methodMapping) == null;
	assert (wasAdded);
	return methodMapping;
    }

    @Override
    public String toString() {
	StringBuilder buf = new StringBuilder();
	buf.append(m_obfName);
	buf.append(" <-> ");
	buf.append(m_deobfName);
	buf.append("\n");
	buf.append("Fields:\n");
	for (FieldMapping fieldMapping : fields()) {
	    buf.append("\t");
	    buf.append(fieldMapping.getObfName());
	    buf.append(" <-> ");
	    buf.append(fieldMapping.getDeobfName());
	    buf.append("\n");
	}
	buf.append("Methods:\n");
	for (MethodMapping methodMapping : m_methodsByObf.values()) {
	    buf.append(methodMapping.toString());
	    buf.append("\n");
	}
	buf.append("Inner Classes:\n");
	for (ClassMapping classMapping : m_innerClassesByObf.values()) {
	    buf.append("\t");
	    buf.append(classMapping.getObfName());
	    buf.append(" <-> ");
	    buf.append(classMapping.getDeobfName());
	    buf.append("\n");
	}
	return buf.toString();
    }

    @Override
    public int compareTo(ClassMapping other) {
	// sort by a, b, c, ... aa, ab, etc
	if (m_obfName.length() != other.m_obfName.length()) {
	    return m_obfName.length() - other.m_obfName.length();
	}
	return m_obfName.compareTo(other.m_obfName);
    }

    public boolean renameObfClass(String oldObfClassName, String newObfClassName) {
	// rename inner classes
	for (ClassMapping innerClassMapping : new ArrayList<ClassMapping>(m_innerClassesByObf.values())) {
	    if (innerClassMapping.renameObfClass(oldObfClassName, newObfClassName)) {
		boolean wasRemoved = m_innerClassesByObf.remove(oldObfClassName) != null;
		assert (wasRemoved);
		boolean wasAdded = m_innerClassesByObf.put(newObfClassName, innerClassMapping) == null;
		assert (wasAdded);
	    }
	}

	// rename method signatures
	for (MethodMapping methodMapping : new ArrayList<MethodMapping>(m_methodsByObf.values())) {
	    String oldMethodKey = getMethodKey(methodMapping.getObfName(), methodMapping.getObfSignature());
	    if (methodMapping.renameObfClass(oldObfClassName, newObfClassName)) {
		boolean wasRemoved = m_methodsByObf.remove(oldMethodKey) != null;
		assert (wasRemoved);
		boolean wasAdded = m_methodsByObf.put(
			getMethodKey(methodMapping.getObfName(), methodMapping.getObfSignature()), methodMapping) == null;
		assert (wasAdded);
	    }
	}

	if (m_obfName.equals(oldObfClassName)) {
	    // rename this class
	    m_obfName = newObfClassName;
	    return true;
	}
	return false;
    }

    public boolean containsArgument(BehaviorEntry obfBehaviorEntry, String name) {
	MethodMapping methodMapping = m_methodsByObf.get(getMethodKey(obfBehaviorEntry.getName(),
		obfBehaviorEntry.getSignature()));
	if (methodMapping != null) {
	    return methodMapping.containsArgument(name);
	}
	return false;
    }

    public static boolean isSimpleClassName(String name) {
	return name.indexOf('/') < 0 && name.indexOf('$') < 0;
    }
}
