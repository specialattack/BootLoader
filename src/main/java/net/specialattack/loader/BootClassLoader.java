package net.specialattack.loader;

import sun.misc.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class BootClassLoader extends URLClassLoader {

    public static final boolean CLASSLOADER_DEBUGGING = Boolean.parseBoolean(System.getProperty("bootloader.classloaderDebugging", "false"));
    private ClassLoader parent = this.getClass().getClassLoader();
    private List<URL> jars;
    private Map<String, Class<?>> classCache = new HashMap<String, Class<?>>();
    private Map<String, byte[]> classBytesCache = new HashMap<String, byte[]>();
    private Set<String> missingClasses = new HashSet<String>();
    private Set<String> badClasses = new HashSet<String>();
    private Set<String> loaderExceptions = new HashSet<String>();
    private Set<String> transformerExceptions = new HashSet<String>();
    private Set<IClassTransformer> classTransformers = new HashSet<IClassTransformer>();
    private Set<IClassInspector> classInspectors = new HashSet<IClassInspector>();

    public BootClassLoader(URL[] jars) {
        super(jars, BootClassLoader.class.getClassLoader());
        this.jars = new ArrayList<URL>(Arrays.asList(jars));

        this.addLoaderException("java.");
        this.addLoaderException("sun.");
        this.addLoaderException("sunw.");
        this.addLoaderException("com.google.gson.");
        this.addLoaderException("com.oracle.");
        this.addLoaderException("com.sun.");
        this.addLoaderException("netscape.");

        this.addTransformerException("javax.");
        this.addTransformerException("com.google.");
        this.addTransformerException("net.specialattack.loader.");
    }

    public void addLoaderException(String path) {
        if (CLASSLOADER_DEBUGGING) {
            System.out.println("Adding class loader exception " + path);
        }
        this.loaderExceptions.add(path);
    }

    public void addTransformerException(String path) {
        if (CLASSLOADER_DEBUGGING) {
            System.out.println("Adding transformer exception " + path);
        }
        this.transformerExceptions.add(path);
    }

    public void addClassTransformer(IClassTransformer transformer) {
        if (CLASSLOADER_DEBUGGING) {
            System.out.println("Adding class transformer " + transformer.getClass());
        }
        this.classTransformers.add(transformer);
    }

    public void addClassInspector(IClassInspector inspector) {
        if (CLASSLOADER_DEBUGGING) {
            System.out.println("Adding class inspector " + inspector.getClass());
        }
        this.classInspectors.add(inspector);
    }

    @Override
    protected void addURL(URL url) {
        this.jars.add(url);
        super.addURL(url);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (this.badClasses.contains(name)) {
            throw new ClassNotFoundException(name);
        }

        if (CLASSLOADER_DEBUGGING) {
            System.out.println("Attempting to find class " + name);
        }

        for (String exception : this.loaderExceptions) {
            if (name.startsWith(exception)) {
                if (CLASSLOADER_DEBUGGING) {
                    System.out.println("Delegating to parent classloader");
                }

                return this.parent.loadClass(name);
            }
        }

        if (this.classCache.containsKey(name)) {
            if (CLASSLOADER_DEBUGGING) {
                System.out.println("Found class in cache");
            }

            return this.classCache.get(name);
        }

        for (String exception : this.transformerExceptions) {
            if (name.startsWith(exception)) {
                if (CLASSLOADER_DEBUGGING) {
                    System.out.println("Loading without transforming");
                }

                try {
                    Class<?> clazz = super.findClass(name);
                    this.classCache.put(name, clazz);
                    return clazz;
                } catch (ClassNotFoundException e) {
                    this.badClasses.add(name);
                    throw e;
                }
            }
        }

        byte[] bytes = this.findBytes(name);

        if (bytes == null) {
            this.badClasses.add(name);
            if (CLASSLOADER_DEBUGGING) {
                System.out.println("Could not find bytes");
            }
            throw new ClassNotFoundException(name);
        }

        for (IClassTransformer transformer : this.classTransformers) {
            if (CLASSLOADER_DEBUGGING) {
                System.out.println("Transforming class with " + transformer);
                System.out.println("Before: " + bytes.length);
            }

            bytes = transformer.transform(name, bytes);
            if (CLASSLOADER_DEBUGGING) {
                System.out.println("After: " + bytes.length);
            }
        }

        Class<?> result;
        try {
            result = this.defineClass(name, bytes, 0, bytes.length);
        } catch (Throwable e) {
            throw new ClassNotFoundException(name, e);
        }

        if (result == null) {
            if (CLASSLOADER_DEBUGGING) {
                System.out.println("Failed defining class");
            }
            throw new ClassNotFoundException(name);
        }

        this.classCache.put(name, result);

        if (CLASSLOADER_DEBUGGING) {
            System.out.println("Defined class " + result);
        }

        return result;
    }

    private byte[] findBytes(String name) {
        if (this.missingClasses.contains(name)) {
            return null;
        }
        if (this.classBytesCache.containsKey(name)) {
            return this.classBytesCache.get(name);
        }

        InputStream in = null;
        try {
            String resource = name.replace('.', '/').concat(".class");
            URL url = this.findResource(resource);

            if (url == null) {
                this.missingClasses.add(name);
                return null;
            }

            in = url.openStream();
            byte[] data = IOUtils.readFully(in, -1, true);
            this.classBytesCache.put(name, data);
            return data;
        } catch (IOException e) {
            return null;
        } finally {
            Util.close(in);
        }
    }

    public void inspectAllClasses() {
        for (URL jarUrl : this.jars) {
            if (CLASSLOADER_DEBUGGING) {
                System.out.println("Inspecting " + jarUrl);
            }

            JarInputStream in = null;

            try {
                in = new JarInputStream(jarUrl.openStream());

                JarEntry entry = null;
                while ((entry = in.getNextJarEntry()) != null) {
                    String name = entry.getName();
                    if (name.endsWith(".class")) {
                        if (CLASSLOADER_DEBUGGING) {
                            System.out.println("Inspecting class file " + name);
                        }

                        byte[] data = IOUtils.readFully(in, -1, true);

                        String className = name.substring(0, name.length() - 6).replaceAll("/", ".");

                        for (IClassInspector inspector : this.classInspectors) {
                            if (CLASSLOADER_DEBUGGING) {
                                System.out.println("Inspecting class file with " + inspector);
                            }
                            inspector.inspect(className, data);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                Util.close(in);
            }
        }
    }

    public Class<?> define(String name, byte[] data) {
        if (CLASSLOADER_DEBUGGING) {
            System.out.println("Defining custom class " + name);
        }

        return this.defineClass(name, data, 0, data.length);
    }

}
