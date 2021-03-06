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

import cuchaz.enigma.analysis.EntryReference;
import cuchaz.enigma.mapping.BehaviorEntry;
import cuchaz.enigma.mapping.ClassEntry;
import cuchaz.enigma.mapping.ConstructorEntry;
import cuchaz.enigma.mapping.FieldEntry;
import cuchaz.enigma.mapping.MethodEntry;

public class EntryFactory
{
	public static ClassEntry newClass( String name )
	{
		return new ClassEntry( name );
	}
	
	public static FieldEntry newField( String className, String fieldName )
	{
		return new FieldEntry( newClass( className ), fieldName );
	}
	
	public static MethodEntry newMethod( String className, String methodName, String methodSignature )
	{
		return new MethodEntry( newClass( className ), methodName, methodSignature );
	}
	
	public static ConstructorEntry newConstructor( String className, String signature )
	{
		return new ConstructorEntry( newClass( className ), signature );
	}
	
	public static EntryReference<FieldEntry,BehaviorEntry> newFieldReferenceByMethod( FieldEntry fieldEntry, String callerClassName, String callerName, String callerSignature )
	{
		return new EntryReference<FieldEntry,BehaviorEntry>( fieldEntry, "", newMethod( callerClassName, callerName, callerSignature ) );
	}
	
	public static EntryReference<FieldEntry,BehaviorEntry> newFieldReferenceByConstructor( FieldEntry fieldEntry, String callerClassName, String callerSignature )
	{
		return new EntryReference<FieldEntry,BehaviorEntry>( fieldEntry, "", newConstructor( callerClassName, callerSignature ) );
	}
	
	public static EntryReference<BehaviorEntry,BehaviorEntry> newBehaviorReferenceByMethod( BehaviorEntry behaviorEntry, String callerClassName, String callerName, String callerSignature )
	{
		return new EntryReference<BehaviorEntry,BehaviorEntry>( behaviorEntry, "", newMethod( callerClassName, callerName, callerSignature ) );
	}
	
	public static EntryReference<BehaviorEntry,BehaviorEntry> newBehaviorReferenceByConstructor( BehaviorEntry behaviorEntry, String callerClassName, String callerSignature )
	{
		return new EntryReference<BehaviorEntry,BehaviorEntry>( behaviorEntry, "", newConstructor( callerClassName, callerSignature ) );
	}
}
