package net.specialattack.loader;

import net.specialattack.loader.config.Configuration;
import net.specialattack.loader.tracking.TrackableClass;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class BootLoader {

    private Set<BootClassLoader> loaders;
    protected static final BootLoader INSTANCE = new BootLoader();
    private int connectionPort = -1;
    private ServerSocket serverSocket;
    private RunnableSocket runnableSocket;
    private Thread threadSocket;
    private List<ServiceData> services = new ArrayList<ServiceData>();

    private BootLoader() {
    }

    private void loadConfig() {
        Configuration config = new Configuration(new File("loader.cfg"));
        config.setDefault("connectionPort", -1);
        config.load();
        this.connectionPort = config.getInt("connectionPort");
    }

    private void setup() throws IOException {
        final Thread.UncaughtExceptionHandler oldHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {

            @Override
            public void uncaughtException(Thread thread, Throwable e) {
                // TODO: Log uncaught exceptions
                if (oldHandler != null) {
                    oldHandler.uncaughtException(thread, e);
                } else {
                    e.printStackTrace();
                }
            }
        });

        // setup remote connection
        if (this.connectionPort > 0) {
            this.serverSocket = new ServerSocket(this.connectionPort);
            this.runnableSocket = new RunnableSocket();
            this.threadSocket = new Thread(this.runnableSocket, "Monitor connection thread");
            this.threadSocket.start();
        }

        // Detect services
        File servicesFolder = new File("services");
        if (!servicesFolder.exists() || !servicesFolder.isDirectory()) {
            if (!servicesFolder.mkdirs()) {
                throw new RuntimeException("Failed creating services folder");
            }
        }

        List<File> filesList = new ArrayList<File>();
        File[] files = servicesFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".jar")) {
                    filesList.add(file);
                }
            }
        }

        if (filesList.size() > 0) {
            for (File file : filesList) {
                System.out.println("Inspecting file " + file);
                BootClassLoader loader = new BootClassLoader(new URL[] { file.toURI().toURL() });
                ServiceData service = new ServiceData(loader);
                loader.addClassInspector(new ServicesInspector(service));
                loader.inspectAllClasses();
                this.services.add(service);
            }
        }

        for (ServiceData service : this.services) {
            for (String className : service.services) {
                try {
                    Class<?> clazz = service.loader.findClass(className);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("Failed loading class '" + className + "', but the class should exist!", e);
                }
            }
        }
    }

    private void startServices() {
        for (ServiceData service : this.services) {
            for (IServiceWrapper wrapper : service.serviceWrappers) {
                wrapper.start();
            }
        }
    }

    private void stopServices() {
        for (ServiceData service : this.services) {
            for (IServiceWrapper wrapper : service.serviceWrappers) {
                wrapper.stop();
            }
        }
    }

    public static void main(String[] args) {
        INSTANCE.loadConfig();
        try {
            INSTANCE.setup();
        } catch (IOException e) {
            System.err.println("Failed setting up connection monitor");
            e.printStackTrace();
            System.exit(1);
        }
        INSTANCE.startServices();
    }

    private class RunnableSocket implements Runnable {

        @Override
        public void run() {
            try {
                Socket socket = BootLoader.this.serverSocket.accept();
                System.out.println("=== Got connection!");
                //LocalServer connection = new LocalServer(this.connectionsList, socket);
                //this.connectionsList.addConnection(connection);
            } catch (SocketException e) {
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static class ServicesInspector implements IClassInspector {

        private final ServiceData service;

        protected ServicesInspector(ServiceData service) {
            this.service = service;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void inspect(String name, byte[] data) {
            ClassReader reader = new ClassReader(data);
            ClassNode node = new ClassNode();
            reader.accept(node, 0);

            List<AnnotationNode> annotations = (List<AnnotationNode>) node.invisibleAnnotations;
            if (annotations != null) {
                for (AnnotationNode annotation : annotations) {
                    if (annotation.desc.equals(Type.getDescriptor(Service.class))) {
                        System.out.println("Detected service class " + name);
                        this.service.services.add(name);
                    } else if (annotation.desc.equals(Type.getDescriptor(TrackableClass.class))) {
                        System.out.println("Detected trackable class " + name);
                        this.service.trackableClasses.add(name);
                    }
                }
            }
        }
    }

}
