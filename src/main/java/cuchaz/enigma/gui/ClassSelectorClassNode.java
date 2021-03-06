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
package cuchaz.enigma.gui;

import javax.swing.tree.DefaultMutableTreeNode;

import cuchaz.enigma.mapping.ClassEntry;

public class ClassSelectorClassNode extends DefaultMutableTreeNode {
    private static final long serialVersionUID = -8956754339813257380L;

    private ClassEntry m_classEntry;

    public ClassSelectorClassNode(ClassEntry classEntry) {
	m_classEntry = classEntry;
    }

    public ClassEntry getClassEntry() {
	return m_classEntry;
    }

    @Override
    public String toString() {
	return m_classEntry.getSimpleName();
    }
}
