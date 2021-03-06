package net.specialattack.loader.logging;

import java.io.PrintStream;
import java.util.logging.ConsoleHandler;
import java.util.logging.LogRecord;

public class ConsoleLogHandler extends ConsoleHandler {

    private PrintStream stream;

    public ConsoleLogHandler(PrintStream stream) {
        this.stream = stream;
    }

    @Override
    public void publish(LogRecord record) {
        this.stream.println(this.getFormatter().format(record));
    }

}
