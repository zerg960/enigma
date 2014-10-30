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

public class ClassSelectorPackageNode extends DefaultMutableTreeNode {
    private static final long serialVersionUID = -3730868701219548043L;

    private String m_packageName;

    public ClassSelectorPackageNode(String packageName) {
	m_packageName = packageName;
    }

    public String getPackageName() {
	return m_packageName;
    }

    @Override
    public String toString() {
	return m_packageName;
    }
}
