/*******************************************************************************
 * Copyright (c) 2014 Jeff Martin.\
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Jeff Martin - initial API and implementation
 ******************************************************************************/
package cuchaz.enigma;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.Test;

import com.google.common.collect.Lists;

import cuchaz.enigma.mapping.ClassEntry;

public class TestDeobfuscator
{
	private Deobfuscator getDeobfuscator( )
	throws IOException
	{
		return new Deobfuscator( new File( "build/libs/testLoneClass.obf.jar" ) );
	}
	
	@Test
	public void loadJar( )
	throws Exception
	{
		getDeobfuscator();
	}
	
	@Test
	public void getClasses( )
	throws Exception
	{
		Deobfuscator deobfuscator = getDeobfuscator();
		List<ClassEntry> obfClasses = Lists.newArrayList();
		List<ClassEntry> deobfClasses = Lists.newArrayList();
		deobfuscator.getSeparatedClasses( obfClasses, deobfClasses );
		assertEquals( 1, obfClasses.size() );
		assertEquals( "none/a", obfClasses.get( 0 ).getName() );
		assertEquals( 1, deobfClasses.size() );
		assertEquals( "cuchaz/enigma/inputs/Keep", deobfClasses.get( 0 ).getName() );
	}
	
	@Test
	public void decompileClass( )
	throws Exception
	{
		Deobfuscator deobfuscator = getDeobfuscator();
		deobfuscator.getSource( deobfuscator.getSourceTree( "none/a" ) );
	}
}
