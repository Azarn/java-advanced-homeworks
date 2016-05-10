package ru.ifmo.ctddev.kichigin.implementor;

import java.io.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.lang.*;
import java.lang.reflect.*;
import java.util.jar.JarOutputStream;
import java.nio.file.*;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import info.kgeorgiy.java.advanced.implementor.Impler;
import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;


public class Implementor implements JarImpler {
    private static final Map<Class, Object> DEFAULT_VALUES;
    private static final String LS = System.lineSeparator();
    private static final String SP = "    ";

    static {
        Map<Class, Object> sMap = new HashMap<Class, Object>();
        sMap.put(boolean.class, false);
        sMap.put(byte.class, "0");
        sMap.put(short.class, "0");
        sMap.put(int.class, "0");
        sMap.put(long.class, "0L");
        sMap.put(char.class, "'\u0000'");
        sMap.put(float.class, "0.0F");
        sMap.put(double.class, "0.0");

        DEFAULT_VALUES = Collections.unmodifiableMap(sMap);
    }

    private HashMap<String, Method> methods;
    private StringBuilder outSB;

    private static String calcMethodHash(Method method) {
        StringBuilder paramsBuilder = new StringBuilder();
        for (Class<?> c: method.getParameterTypes()) {
            paramsBuilder.append(c.getCanonicalName());
        }

        return method.getName() + paramsBuilder.toString();
    }

    private void loadAbstractMethods(Class<?> clazz, boolean iterateInterfaces) {
        for (Method method: clazz.getDeclaredMethods()) {
            int mods = method.getModifiers();
            String methodHash = calcMethodHash(method);
            if (Modifier.isPrivate(mods) || methods.containsKey(methodHash)) {
                    continue;
            } else {
                methods.put(methodHash, method);
            }
        }

        if (iterateInterfaces) {
            for(Class<?> iface: clazz.getInterfaces()) {
                loadAbstractMethods(iface, iterateInterfaces);
            }
        } else {
            Class<?> superclass = clazz.getSuperclass();
            if (superclass != null) {
                loadAbstractMethods(superclass, iterateInterfaces);
            }
        }
    }

    private void implementExecutable(Executable ex, String className) {
        int modifiers = ex.getModifiers() & ~(Modifier.ABSTRACT | Modifier.TRANSIENT | Modifier.NATIVE);
        boolean isVarArgs = ex.isVarArgs();
        boolean isMethod = ex.getClass() == Method.class;

        outSB.append(LS + LS + SP + Modifier.toString(modifiers) + " ");
        Class<?> returnType = null;
        if (isMethod) {
            returnType = ((Method)(ex)).getReturnType();
            outSB.append(returnType.getCanonicalName() + " ");
            outSB.append(ex.getName());
        } else {
            outSB.append(className);
        }

        outSB.append("(");

        Class<?> params[] = ex.getParameterTypes();
        StringBuilder superArgs = new StringBuilder();

        for (int i = 0; i < params.length; ++i) {
            if (isVarArgs && i == params.length - 1) {
                outSB.append(params[i].getComponentType().getCanonicalName());
                outSB.append(" ... varArgs");
                superArgs.append("varArgs");
            } else {
                outSB.append(params[i].getCanonicalName() + " arg" + i);
                superArgs.append("arg" + i);
            }

            if (i != params.length - 1) {
                outSB.append(", ");
                superArgs.append(", ");
            }
        }

        outSB.append(")");

        Class<?> exceptions[] = ex.getExceptionTypes();
        if (exceptions.length > 0) {
            outSB.append(" throws ");
            for (int i = 0; i < exceptions.length; ++i) {
                outSB.append(exceptions[i].getCanonicalName());
                if (i != exceptions.length - 1) {
                    outSB.append(", ");
                }
            }
        }

        outSB.append(" {" + LS + SP + SP);

        if (!isMethod) {
            outSB.append("super(" + superArgs.toString() + ")");
        } else {
            outSB.append("return");

            if (returnType.isPrimitive()) {
                if (!(returnType == void.class)) {
                    outSB.append(" ");
                    outSB.append(DEFAULT_VALUES.get(returnType));
                }
            } else {
                outSB.append(" null");
            }
        }

        outSB.append(";").append(LS).append(SP).append("}").append(LS);
    }

	public void implement(Class<?> token, Path root) throws ImplerException {
        methods = new HashMap<>();
        outSB = new StringBuilder();
        String newClassName = token.getSimpleName() + "Impl";

        int classMods = token.getModifiers();

        if (Modifier.isFinal(classMods)) {
            throw new ImplerException("Cannot implement final class!");
        }

        Package pkg = token.getPackage();
        if (pkg != null) {
            outSB.append("package " + pkg.getName() + ";" + LS);
        }

        outSB.append(Modifier.toString(Modifier.classModifiers() & classMods & ~Modifier.ABSTRACT));
        outSB.append(" class " + newClassName);

        if (Modifier.isInterface(classMods)) {
            loadAbstractMethods(token, true);
            outSB.append(" implements ");
        } else {
            loadAbstractMethods(token, false);
            outSB.append(" extends ");
        }
        outSB.append(token.getCanonicalName() + " {" + LS);

        int privateCount = 0;
        for (Constructor ctr: token.getDeclaredConstructors()) {
            int modifiers = ctr.getModifiers();
            if (Modifier.isPrivate(modifiers)) {
                privateCount++;
                continue;
            }
            implementExecutable(ctr, newClassName);
        }

        if (privateCount > 0 && privateCount == token.getDeclaredConstructors().length) {
            throw new ImplerException("Cannot inherit from class that, all constuctors are private");
        }

        for (Map.Entry<String, Method> mapSet: methods.entrySet()) {
            Method method = mapSet.getValue();
            if (Modifier.isAbstract(method.getModifiers())) {
                implementExecutable(method, newClassName);
            }
        }

        outSB.append("}");

        root = getDirPath(root, token, File.separator);
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new ImplerException("Cannot create directories for the package");
        }

        try (BufferedWriter bw = Files.newBufferedWriter(root.resolve(newClassName + ".java"))) {
            bw.write(outSB.toString());
        } catch (IOException e) {
            throw new ImplerException("Error while writing to output file");
        }
    }

    public static Path getDirPath(Path root, Class<?> token, String separator) {
        Package pkg = token.getPackage();
        if (pkg != null) {
            root = root.resolve(pkg.getName().replace(".", separator));
        }
        return root;
    }

    public void implementJar(Class<?> token, Path jarFile) throws ImplerException {
        Path dir;
        try {
            dir = Files.createTempDirectory(Paths.get(System.getProperty("java.io.tmpdir")), token.getName());
        } catch (IOException e) {
            throw new ImplerException("Cannot create temp directory!");
        }

        implement(token, dir);

        String newClassName = token.getSimpleName() + "Impl";
        Path out = getDirPath(dir, token, File.separator);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        compiler.run(null, null, null, out.resolve(newClassName + ".java").toString());

        Package pkg = token.getPackage();
        String filename = "";
        if (pkg != null) {
            filename += pkg.getName().replace(".", "/") + "/";
        }
        filename += newClassName  + ".class";

        try (OutputStream os = Files.newOutputStream(jarFile);
             JarOutputStream jar = new JarOutputStream(os, new Manifest())) {
                jar.putNextEntry(new ZipEntry(filename));
                jar.write(Files.readAllBytes(out.resolve(newClassName+ ".class")));
        } catch (IOException e) {
            throw new ImplerException(e.toString());
        }
    }

    public static void main(String args[]) throws ClassNotFoundException, ImplerException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage Implementor.jar <token> <path>");
        }

        new Implementor().implementJar(Class.forName(args[0]), Paths.get(args[1]));
    }
}
