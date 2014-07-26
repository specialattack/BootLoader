package net.specialattack.loader;

import net.specialattack.loader.config.Configuration;
import net.specialattack.loader.logging.*;
import net.specialattack.loader.tracking.TrackableClass;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public final class BootLoader {

    protected static final BootLoader INSTANCE = new BootLoader();
    private Set<BootClassLoader> loaders;
    private int connectionPort = -1;
    private String logFile;
    private ServerSocket serverSocket;
    private RunnableSocket runnableSocket;
    private Thread threadSocket;
    private List<ServiceData> services = new ArrayList<ServiceData>();
    private PrintStream stdOut;
    private PrintStream stdErr;

    private BootLoader() {
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

    private void loadConfig() {
        Configuration config = new Configuration(new File("loader.cfg"));
        config.setDefault("connectionPort", -1);
        config.setDefault("log-file", "./console.log");
        config.load();
        this.connectionPort = config.getInt("connectionPort");
        this.logFile = config.getString("log-file");
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

        setupLoggers();

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

    private void setupLoggers() {
        if (stdOut != null) {
            resetLoggers();
        }
        stdOut = System.out;
        stdErr = System.err;

        Logger stdout = Logger.getLogger("STDOUT");
        Logger stderr = Logger.getLogger("STDERR");
        Logger global = Logger.getLogger("");
        Logger rawIRC = Logger.getLogger("RawIRC");
        stdout.setUseParentHandlers(false);
        stderr.setUseParentHandlers(false);
        global.setUseParentHandlers(false);
        rawIRC.setUseParentHandlers(false);

        // Disable stupid logger
        Logger httpURLConnection = Logger.getLogger("sun.net.www.protocol.http.HttpURLConnection");
        httpURLConnection.setLevel(Level.OFF);

        ConsoleLogHandler stdoutHandler = new ConsoleLogHandler(System.out);
        ConsoleLogHandler stderrHandler = new ConsoleLogHandler(System.err);
        ConsoleLogFormatter formatter = new ConsoleLogFormatter();
        stdoutHandler.setFormatter(formatter);
        stdoutHandler.setLevel(Level.INFO);
        stderrHandler.setFormatter(formatter);
        stderrHandler.setLevel(Level.INFO);

        stdout.addHandler(stdoutHandler);
        stdout.setLevel(Level.ALL);
        stderr.addHandler(stderrHandler);
        stderr.setLevel(Level.ALL);

        for (Handler handler : global.getHandlers()) {
            global.removeHandler(handler);
        }
        global.addHandler(stdoutHandler);
        global.setLevel(Level.ALL);

        FileLogHandler fileHandler = null;
        try {
            fileHandler = new FileLogHandler("./raw.log", true);
            fileHandler.setFormatter(new FileLogFormatter());
            fileHandler.setLevel(Level.ALL);
            rawIRC.addHandler(fileHandler);

            fileHandler = new FileLogHandler(logFile, true);
            fileHandler.setFormatter(new FileLogFormatter());
            fileHandler.setLevel(Level.ALL);
            stdout.addHandler(fileHandler);
            stderr.addHandler(fileHandler);
            global.addHandler(fileHandler);
        } catch (Throwable e) {
            e.printStackTrace();
        }

        System.setOut(new PrintStream(new LoggerOutputStream(stdout, Level.INFO), true));
        System.setErr(new PrintStream(new LoggerOutputStream(stderr, Level.WARNING), true));
    }

    private void resetLoggers() {
        System.setOut(stdOut);
        System.setErr(stdErr);
        LogManager.getLogManager().reset();
    }

    private void stopServices() {
        for (ServiceData service : this.services) {
            for (IServiceWrapper wrapper : service.serviceWrappers) {
                wrapper.stop();
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

}
