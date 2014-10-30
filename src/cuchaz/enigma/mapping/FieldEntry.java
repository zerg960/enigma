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

import cuchaz.enigma.Util;

public class FieldEntry implements Entry, Serializable {
    private static final long serialVersionUID = 3004663582802885451L;

    private ClassEntry m_classEntry;
    private String m_name;

    // NOTE: this argument order is important for the MethodReader/MethodWriter
    public FieldEntry(ClassEntry classEntry, String name) {
	if (classEntry == null) {
	    throw new IllegalArgumentException("Class cannot be null!");
	}
	if (name == null) {
	    throw new IllegalArgumentException("Field name cannot be null!");
	}

	m_classEntry = classEntry;
	m_name = name;
    }

    public FieldEntry(FieldEntry other) {
	m_classEntry = new ClassEntry(other.m_classEntry);
	m_name = other.m_name;
    }

    public FieldEntry(FieldEntry other, String newClassName) {
	m_classEntry = new ClassEntry(newClassName);
	m_name = other.m_name;
    }

    @Override
    public ClassEntry getClassEntry() {
	return m_classEntry;
    }

    @Override
    public String getName() {
	return m_name;
    }

    @Override
    public String getClassName() {
	return m_classEntry.getName();
    }

    @Override
    public FieldEntry cloneToNewClass(ClassEntry classEntry) {
	return new FieldEntry(this, classEntry.getName());
    }

    @Override
    public int hashCode() {
	return Util.combineHashesOrdered(m_classEntry, m_name);
    }

    @Override
    public boolean equals(Object other) {
	if (other instanceof FieldEntry) {
	    return equals((FieldEntry) other);
	}
	return false;
    }

    public boolean equals(FieldEntry other) {
	return m_classEntry.equals(other.m_classEntry) && m_name.equals(other.m_name);
    }

    @Override
    public String toString() {
	return m_classEntry.getName() + "." + m_name;
    }
}
