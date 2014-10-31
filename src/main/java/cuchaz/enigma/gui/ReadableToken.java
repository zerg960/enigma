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

public class ReadableToken {
    public int line;
    public int startColumn;
    public int endColumn;

    public ReadableToken(int line, int startColumn, int endColumn) {
	this.line = line;
	this.startColumn = startColumn;
	this.endColumn = endColumn;
    }

    @Override
    public String toString() {
	StringBuilder buf = new StringBuilder();
	buf.append("line ");
	buf.append(line);
	buf.append(" columns ");
	buf.append(startColumn);
	buf.append("-");
	buf.append(endColumn);
	return buf.toString();
    }
}
