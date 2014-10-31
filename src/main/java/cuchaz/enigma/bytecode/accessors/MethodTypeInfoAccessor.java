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
package cuchaz.enigma.bytecode.accessors;

import java.lang.reflect.Field;

public class MethodTypeInfoAccessor {
    private static Class<?> m_class;
    private static Field m_descriptorIndex;

    static {
	try {
	    m_class = Class.forName("javassist.bytecode.MethodTypeInfo");
	    m_descriptorIndex = m_class.getDeclaredField("descriptor");
	    m_descriptorIndex.setAccessible(true);
	} catch (Exception ex) {
	    throw new Error(ex);
	}
    }

    public static boolean isType(ConstInfoAccessor accessor) {
	return m_class.isAssignableFrom(accessor.getItem().getClass());
    }

    private Object m_item;

    public MethodTypeInfoAccessor(Object item) {
	m_item = item;
    }

    public int getTypeIndex() {
	try {
	    return (Integer) m_descriptorIndex.get(m_item);
	} catch (Exception ex) {
	    throw new Error(ex);
	}
    }

    public void setTypeIndex(int val) {
	try {
	    m_descriptorIndex.set(m_item, val);
	} catch (Exception ex) {
	    throw new Error(ex);
	}
    }
}
