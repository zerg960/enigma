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

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import javassist.CtClass;
import javassist.bytecode.Descriptor;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.strobel.assembler.metadata.MetadataSystem;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.decompiler.DecompilerContext;
import com.strobel.decompiler.DecompilerSettings;
import com.strobel.decompiler.PlainTextOutput;
import com.strobel.decompiler.languages.java.JavaOutputVisitor;
import com.strobel.decompiler.languages.java.ast.AstBuilder;
import com.strobel.decompiler.languages.java.ast.CompilationUnit;
import com.strobel.decompiler.languages.java.ast.InsertParenthesesVisitor;

import cuchaz.enigma.analysis.EntryReference;
import cuchaz.enigma.analysis.JarClassIterator;
import cuchaz.enigma.analysis.JarIndex;
import cuchaz.enigma.analysis.SourceIndex;
import cuchaz.enigma.analysis.SourceIndexVisitor;
import cuchaz.enigma.analysis.Token;
import cuchaz.enigma.mapping.ArgumentEntry;
import cuchaz.enigma.mapping.BehaviorEntry;
import cuchaz.enigma.mapping.BehaviorEntryFactory;
import cuchaz.enigma.mapping.ClassEntry;
import cuchaz.enigma.mapping.ClassMapping;
import cuchaz.enigma.mapping.ConstructorEntry;
import cuchaz.enigma.mapping.Entry;
import cuchaz.enigma.mapping.FieldEntry;
import cuchaz.enigma.mapping.FieldMapping;
import cuchaz.enigma.mapping.Mappings;
import cuchaz.enigma.mapping.MappingsRenamer;
import cuchaz.enigma.mapping.MethodEntry;
import cuchaz.enigma.mapping.MethodMapping;
import cuchaz.enigma.mapping.TranslationDirection;
import cuchaz.enigma.mapping.Translator;

public class Deobfuscator {
    public interface ProgressListener {
	void init(int totalWork, String title);

	void onProgress(int numDone, String message);
    }

    private File m_file;
    private JarFile m_jar;
    private DecompilerSettings m_settings;
    private JarIndex m_jarIndex;
    private Mappings m_mappings;
    private MappingsRenamer m_renamer;
    private Map<TranslationDirection, Translator> m_translatorCache;

    public Deobfuscator(File file) throws IOException {
	m_file = file;
	m_jar = new JarFile(m_file);

	// build the jar index
	m_jarIndex = new JarIndex();
	m_jarIndex.indexJar(m_jar, true);

	// config the decompiler
	m_settings = DecompilerSettings.javaDefaults();
	m_settings.setMergeVariables(true);
	m_settings.setForceExplicitImports(true);
	m_settings.setForceExplicitTypeArguments(true);
	// DEBUG
	// m_settings.setShowSyntheticMembers( true );

	// init defaults
	m_translatorCache = Maps.newTreeMap();

	// init mappings
	setMappings(new Mappings());
    }

    public String getJarName() {
	return m_file.getName();
    }

    public JarIndex getJarIndex() {
	return m_jarIndex;
    }

    public Mappings getMappings() {
	return m_mappings;
    }

    public void setMappings(Mappings val) {
	if (val == null) {
	    val = new Mappings();
	}

	// pass 1: look for any classes that got moved to inner classes
	Map<String, String> renames = Maps.newHashMap();
	for (ClassMapping classMapping : val.classes()) {
	    // make sure we strip the packages off of obfuscated inner classes
	    String innerClassName = new ClassEntry(classMapping.getObfName()).getSimpleName();
	    String outerClassName = m_jarIndex.getOuterClass(innerClassName);
	    if (outerClassName != null) {
		// build the composite class name
		String newName = outerClassName + "$" + innerClassName;

		// add a rename
		renames.put(classMapping.getObfName(), newName);

		System.out
			.println(String.format("Converted class mapping %s to %s", classMapping.getObfName(), newName));
	    }
	}
	for (Map.Entry<String, String> entry : renames.entrySet()) {
	    val.renameObfClass(entry.getKey(), entry.getValue());
	}

	// pass 2: look for fields/methods that are actually declared in
	// superclasses
	MappingsRenamer renamer = new MappingsRenamer(m_jarIndex, val);
	for (ClassMapping classMapping : val.classes()) {
	    ClassEntry obfClassEntry = new ClassEntry(classMapping.getObfName());

	    // fields
	    for (FieldMapping fieldMapping : Lists.newArrayList(classMapping.fields())) {
		FieldEntry fieldEntry = new FieldEntry(obfClassEntry, fieldMapping.getObfName());
		ClassEntry resolvedObfClassEntry = m_jarIndex.resolveEntryClass(fieldEntry);
		if (resolvedObfClassEntry != null && !resolvedObfClassEntry.equals(fieldEntry.getClassEntry())) {
		    boolean wasMoved = renamer.moveFieldToObfClass(classMapping, fieldMapping, resolvedObfClassEntry);
		    if (wasMoved) {
			System.out.println(String.format("Moved field %s to class %s", fieldEntry,
				resolvedObfClassEntry));
		    } else {
			System.err
				.println(String
					.format("WARNING: Would move field %s to class %s but the field was already there. Dropping instead.",
						fieldEntry, resolvedObfClassEntry));
		    }
		}
	    }

	    // methods
	    for (MethodMapping methodMapping : Lists.newArrayList(classMapping.methods())) {
		// skip constructors
		if (methodMapping.isConstructor()) {
		    continue;
		}

		MethodEntry methodEntry = new MethodEntry(obfClassEntry, methodMapping.getObfName(),
			methodMapping.getObfSignature());
		ClassEntry resolvedObfClassEntry = m_jarIndex.resolveEntryClass(methodEntry);
		if (resolvedObfClassEntry != null && !resolvedObfClassEntry.equals(methodEntry.getClassEntry())) {
		    boolean wasMoved = renamer.moveMethodToObfClass(classMapping, methodMapping, resolvedObfClassEntry);
		    if (wasMoved) {
			System.out.println(String.format("Moved method %s to class %s", methodEntry,
				resolvedObfClassEntry));
		    } else {
			System.err
				.println(String
					.format("WARNING: Would move method %s to class %s but the method was already there. Dropping instead.",
						methodEntry, resolvedObfClassEntry));
		    }
		}
	    }

	    // TODO: recurse to inner classes?
	}

	// drop mappings that don't match the jar
	List<ClassEntry> unknownClasses = Lists.newArrayList();
	for (ClassMapping classMapping : val.classes()) {
	    checkClassMapping(unknownClasses, classMapping);
	}
	if (!unknownClasses.isEmpty()) {
	    throw new Error("Unable to find classes in jar: " + unknownClasses);
	}

	m_mappings = val;
	m_renamer = renamer;
	m_translatorCache.clear();
    }

    private void checkClassMapping(List<ClassEntry> unknownClasses, ClassMapping classMapping) {
	// check the class
	ClassEntry classEntry = new ClassEntry(classMapping.getObfName());
	String outerClassName = m_jarIndex.getOuterClass(classEntry.getSimpleName());
	if (outerClassName != null) {
	    classEntry = new ClassEntry(outerClassName + "$" + classMapping.getObfName());
	}
	if (!m_jarIndex.getObfClassEntries().contains(classEntry)) {
	    unknownClasses.add(classEntry);
	}

	// check the fields
	for (FieldMapping fieldMapping : Lists.newArrayList(classMapping.fields())) {
	    FieldEntry fieldEntry = new FieldEntry(classEntry, fieldMapping.getObfName());
	    if (!m_jarIndex.containsObfField(fieldEntry)) {
		System.err.println("WARNING: unable to find field " + fieldEntry + ". dropping mapping.");
		classMapping.removeFieldMapping(fieldMapping);
	    }
	}

	// check methods
	for (MethodMapping methodMapping : Lists.newArrayList(classMapping.methods())) {
	    BehaviorEntry obfBehaviorEntry = BehaviorEntryFactory.createObf(classEntry, methodMapping);
	    if (!m_jarIndex.containsObfBehavior(obfBehaviorEntry)) {
		System.err.println("WARNING: unable to find behavior " + obfBehaviorEntry + ". dropping mapping.");
		classMapping.removeMethodMapping(methodMapping);
	    }
	}

	// check inner classes
	for (ClassMapping innerClassMapping : classMapping.innerClasses()) {
	    checkClassMapping(unknownClasses, innerClassMapping);
	}
    }

    public Translator getTranslator(TranslationDirection direction) {
	Translator translator = m_translatorCache.get(direction);
	if (translator == null) {
	    translator = m_mappings.getTranslator(direction);
	    m_translatorCache.put(direction, translator);
	}
	return translator;
    }

    public void getSeparatedClasses(List<ClassEntry> obfClasses, List<ClassEntry> deobfClasses) {
	for (ClassEntry obfClassEntry : m_jarIndex.getObfClassEntries()) {
	    // skip inner classes
	    if (obfClassEntry.isInnerClass()) {
		continue;
	    }

	    // separate the classes
	    ClassEntry deobfClassEntry = deobfuscateEntry(obfClassEntry);
	    if (!deobfClassEntry.equals(obfClassEntry)) {
		// if the class has a mapping, clearly it's deobfuscated
		deobfClasses.add(deobfClassEntry);
	    } else if (!obfClassEntry.getPackageName().equals(Constants.NonePackage)) {
		// also call it deobufscated if it's not in the none package
		deobfClasses.add(obfClassEntry);
	    } else {
		// otherwise, assume it's still obfuscated
		obfClasses.add(obfClassEntry);
	    }
	}
    }

    public CompilationUnit getSourceTree(String obfClassName) {
	// is this class deobfuscated?
	// we need to tell the decompiler the deobfuscated name so it doesn't
	// get freaked out
	// the decompiler only sees the deobfuscated class, so we need to load
	// it by the deobfuscated name
	String lookupClassName = obfClassName;
	ClassMapping classMapping = m_mappings.getClassByObf(obfClassName);
	if (classMapping != null && classMapping.getDeobfName() != null) {
	    lookupClassName = classMapping.getDeobfName();
	}

	// is this class even in the jar?
	if (!m_jarIndex.containsObfClass(new ClassEntry(obfClassName))) {
	    return null;
	}

	// set the type loader
	m_settings.setTypeLoader(new TranslatingTypeLoader(m_jar, m_jarIndex,
		getTranslator(TranslationDirection.Obfuscating), getTranslator(TranslationDirection.Deobfuscating)));

	// decompile it!
	TypeDefinition resolvedType = new MetadataSystem(m_settings.getTypeLoader()).lookupType(lookupClassName)
		.resolve();
	DecompilerContext context = new DecompilerContext();
	context.setCurrentType(resolvedType);
	context.setSettings(m_settings);
	AstBuilder builder = new AstBuilder(context);
	builder.addType(resolvedType);
	builder.runTransformations(null);
	return builder.getCompilationUnit();
    }

    public SourceIndex getSourceIndex(CompilationUnit sourceTree, String source) {
	// build the source index
	SourceIndex index = new SourceIndex(source);
	sourceTree.acceptVisitor(new SourceIndexVisitor(), index);

	// DEBUG
	// sourceTree.acceptVisitor( new TreeDumpVisitor( new File( "tree.txt" )
	// ), null );

	// resolve all the classes in the source references
	for (Token token : index.referenceTokens()) {
	    EntryReference<Entry, Entry> deobfReference = index.getDeobfReference(token);

	    // get the obfuscated entry
	    Entry obfEntry = obfuscateEntry(deobfReference.entry);

	    // try to resolve the class
	    ClassEntry resolvedObfClassEntry = m_jarIndex.resolveEntryClass(obfEntry);
	    if (resolvedObfClassEntry != null && !resolvedObfClassEntry.equals(obfEntry.getClassEntry())) {
		// change the class of the entry
		obfEntry = obfEntry.cloneToNewClass(resolvedObfClassEntry);

		// save the new deobfuscated reference
		deobfReference.entry = deobfuscateEntry(obfEntry);
		index.replaceDeobfReference(token, deobfReference);
	    }

	    // DEBUG
	    // System.out.println( token + " -> " + reference + " -> " +
	    // index.getReferenceToken( reference ) );
	}

	return index;
    }

    public String getSource(CompilationUnit sourceTree) {
	// render the AST into source
	StringWriter buf = new StringWriter();
	sourceTree.acceptVisitor(new InsertParenthesesVisitor(), null);
	sourceTree.acceptVisitor(new JavaOutputVisitor(new PlainTextOutput(buf), m_settings), null);
	return buf.toString();
    }

    public void writeSources(File dirOut, ProgressListener progress) throws IOException {
	// get the classes to decompile
	Set<ClassEntry> classEntries = Sets.newHashSet();
	for (ClassEntry obfClassEntry : m_jarIndex.getObfClassEntries()) {
	    // skip inner classes
	    if (obfClassEntry.isInnerClass()) {
		continue;
	    }

	    classEntries.add(obfClassEntry);
	}

	if (progress != null) {
	    progress.init(classEntries.size(), "Decompiling classes...");
	}

	// DEOBFUSCATE ALL THE THINGS!! @_@
	int i = 0;
	for (ClassEntry obfClassEntry : classEntries) {
	    ClassEntry deobfClassEntry = deobfuscateEntry(new ClassEntry(obfClassEntry));
	    if (progress != null) {
		progress.onProgress(i++, deobfClassEntry.toString());
	    }

	    try {
		// get the source
		String source = getSource(getSourceTree(obfClassEntry.getName()));

		// write the file
		File file = new File(dirOut, deobfClassEntry.getName().replace('.', '/') + ".java");
		file.getParentFile().mkdirs();
		try (FileWriter out = new FileWriter(file)) {
		    out.write(source);
		}
	    } catch (Throwable t) {
		throw new Error("Unable to deobfuscate class " + deobfClassEntry.toString() + " ("
			+ obfClassEntry.toString() + ")", t);
	    }
	}
	if (progress != null) {
	    progress.onProgress(i, "Done!");
	}
    }

    public void writeJar(File out, ProgressListener progress) {
	try (JarOutputStream outJar = new JarOutputStream(new FileOutputStream(out))) {
	    if (progress != null) {
		progress.init(JarClassIterator.getClassEntries(m_jar).size(), "Translating classes...");
	    }

	    // prep the loader
	    TranslatingTypeLoader loader = new TranslatingTypeLoader(m_jar, m_jarIndex,
		    getTranslator(TranslationDirection.Obfuscating), getTranslator(TranslationDirection.Deobfuscating));

	    int i = 0;
	    for (CtClass c : JarClassIterator.classes(m_jar)) {
		if (progress != null) {
		    progress.onProgress(i++, c.getName());
		}

		try {
		    c = loader.transformClass(c);
		    outJar.putNextEntry(new JarEntry(c.getName().replace('.', '/') + ".class"));
		    outJar.write(c.toBytecode());
		    outJar.closeEntry();
		} catch (Throwable t) {
		    throw new Error("Unable to deobfuscate class " + c.getName(), t);
		}
	    }
	    if (progress != null) {
		progress.onProgress(i, "Done!");
	    }

	    outJar.close();
	} catch (IOException ex) {
	    throw new Error("Unable to write to Jar file!");
	}
    }

    public <T extends Entry> T obfuscateEntry(T deobfEntry) {
	if (deobfEntry == null) {
	    return null;
	}
	return getTranslator(TranslationDirection.Obfuscating).translateEntry(deobfEntry);
    }

    public <T extends Entry> T deobfuscateEntry(T obfEntry) {
	if (obfEntry == null) {
	    return null;
	}
	return getTranslator(TranslationDirection.Deobfuscating).translateEntry(obfEntry);
    }

    public <E extends Entry, C extends Entry> EntryReference<E, C> obfuscateReference(
	    EntryReference<E, C> deobfReference) {
	if (deobfReference == null) {
	    return null;
	}
	return new EntryReference<E, C>(obfuscateEntry(deobfReference.entry), obfuscateEntry(deobfReference.context),
		deobfReference);
    }

    public <E extends Entry, C extends Entry> EntryReference<E, C> deobfuscateReference(
	    EntryReference<E, C> obfReference) {
	if (obfReference == null) {
	    return null;
	}
	return new EntryReference<E, C>(deobfuscateEntry(obfReference.entry), deobfuscateEntry(obfReference.context),
		obfReference);
    }

    public boolean isObfuscatedIdentifier(Entry obfEntry) {
	return m_jarIndex.containsObfEntry(obfEntry);
    }

    public boolean isRenameable(EntryReference<Entry, Entry> obfReference) {
	return obfReference.isNamed() && isObfuscatedIdentifier(obfReference.getNameableEntry());
    }

    // NOTE: these methods are a bit messy... oh well

    public boolean hasDeobfuscatedName(Entry obfEntry) {
	Translator translator = getTranslator(TranslationDirection.Deobfuscating);
	if (obfEntry instanceof ClassEntry) {
	    return translator.translate((ClassEntry) obfEntry) != null;
	} else if (obfEntry instanceof FieldEntry) {
	    return translator.translate((FieldEntry) obfEntry) != null;
	} else if (obfEntry instanceof MethodEntry) {
	    return translator.translate((MethodEntry) obfEntry) != null;
	} else if (obfEntry instanceof ConstructorEntry) {
	    // constructors have no names
	    return false;
	} else if (obfEntry instanceof ArgumentEntry) {
	    return translator.translate((ArgumentEntry) obfEntry) != null;
	} else {
	    throw new Error("Unknown entry type: " + obfEntry.getClass().getName());
	}
    }

    public void rename(Entry obfEntry, String newName) {
	if (obfEntry instanceof ClassEntry) {
	    m_renamer.setClassName((ClassEntry) obfEntry, Descriptor.toJvmName(newName));
	} else if (obfEntry instanceof FieldEntry) {
	    m_renamer.setFieldName((FieldEntry) obfEntry, newName);
	} else if (obfEntry instanceof MethodEntry) {
	    m_renamer.setMethodTreeName((MethodEntry) obfEntry, newName);
	} else if (obfEntry instanceof ConstructorEntry) {
	    throw new IllegalArgumentException("Cannot rename constructors");
	} else if (obfEntry instanceof ArgumentEntry) {
	    m_renamer.setArgumentName((ArgumentEntry) obfEntry, newName);
	} else {
	    throw new Error("Unknown entry type: " + obfEntry.getClass().getName());
	}

	// clear caches
	m_translatorCache.clear();
    }

    public void removeMapping(Entry obfEntry) {
	if (obfEntry instanceof ClassEntry) {
	    m_renamer.removeClassMapping((ClassEntry) obfEntry);
	} else if (obfEntry instanceof FieldEntry) {
	    m_renamer.removeFieldMapping((FieldEntry) obfEntry);
	} else if (obfEntry instanceof MethodEntry) {
	    m_renamer.removeMethodTreeMapping((MethodEntry) obfEntry);
	} else if (obfEntry instanceof ConstructorEntry) {
	    throw new IllegalArgumentException("Cannot rename constructors");
	} else if (obfEntry instanceof ArgumentEntry) {
	    m_renamer.removeArgumentMapping((ArgumentEntry) obfEntry);
	} else {
	    throw new Error("Unknown entry type: " + obfEntry);
	}

	// clear caches
	m_translatorCache.clear();
    }

    public void markAsDeobfuscated(Entry obfEntry) {
	if (obfEntry instanceof ClassEntry) {
	    m_renamer.markClassAsDeobfuscated((ClassEntry) obfEntry);
	} else if (obfEntry instanceof FieldEntry) {
	    m_renamer.markFieldAsDeobfuscated((FieldEntry) obfEntry);
	} else if (obfEntry instanceof MethodEntry) {
	    m_renamer.markMethodTreeAsDeobfuscated((MethodEntry) obfEntry);
	} else if (obfEntry instanceof ConstructorEntry) {
	    throw new IllegalArgumentException("Cannot rename constructors");
	} else if (obfEntry instanceof ArgumentEntry) {
	    m_renamer.markArgumentAsDeobfuscated((ArgumentEntry) obfEntry);
	} else {
	    throw new Error("Unknown entry type: " + obfEntry);
	}

	// clear caches
	m_translatorCache.clear();
    }
}
