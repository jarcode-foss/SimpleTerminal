
package promptemulator;

import javax.swing.*;
import java.awt.*; 
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.text.DefaultCaret;

public class PromptEmulator extends JPanel {
    
    public static void main(String[] args) {
        JFrame frame = new JFrame();
        frame.setTitle("Prompt Emulator");
        frame.setSize(600, 400);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new PromptEmulator());
        frame.setVisible(true);
    }
    
    private JTextArea area = new JTextArea();
    private JTextField field = new JTextField();
    private Thread inputStreamThread = null;
    private Thread errorStreamThread = null;
    private OutputStream outputStream = null;
    private volatile boolean running = false;
    
    private File dir = new File(System.getProperty("user.dir"));
    private HashMap<String, CommandListener> commands = new HashMap<>();
    
    private Object LOCK = new Object();
    
    {
        CommandListener showDirectory = new CommandListener() {
            public void invoke(String args[]) {
                String path;
                String text = "";
                synchronized(LOCK) {
                    path = dir.getAbsolutePath();
                    for (File file : dir.listFiles()) {
                        text += file.getName() + " (file)\t";
                    }
                }
                append("\nWorking directory: " + path);
                append("\n" + text + "\n");
            }
        };
        commands.put("dir", showDirectory);
        commands.put("ls", showDirectory);
        commands.put("clear", new CommandListener() {
            public void invoke(String args[]) {
                area.setText("");
            }
        });
        commands.put("cd", new CommandListener() {
            public void invoke(String args[]) {
                if (args.length <= 1) {
                    append("\nUsage: cd [directory]");
                    return;
                }
                String path = "";
                for (int t = 1; t < args.length; t++)
                    path += args[t] + " ";
                path.trim();
                if (path.startsWith("\"") && path.endsWith("\""))
                    path = path.substring(1, path.length() - 1);
                boolean changed = false;
                block: synchronized(LOCK) {
                    File pathFile = new File(dir.getAbsolutePath() + File.separator + path);
                    if (pathFile.exists() && pathFile.isDirectory()) {
                        dir = pathFile;
                        changed = true;
                        break block;
                    }
                    File rootFile = new File(path);
                    if (rootFile.exists() && rootFile.isDirectory()) {
                        dir = rootFile;
                        changed = true;
                        break block;
                    }
                }
                if (changed) {
                    append(String.format("\nWorking directory changed to '%s'", dir.getPath()));
                }
                else {
                    append(String.format("\nDirectory '%s' does not exist", path));
                }
            }
        });
    }
    
    public PromptEmulator() {
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setBackground(Color.DARK_GRAY);
        area.setForeground(Color.WHITE);
        area.setFont(new Font(Font.MONOSPACED, 0, 12));
        field.addActionListener(new FieldListener());
        JScrollPane pane = new JScrollPane(area);
        ((DefaultCaret) area.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        this.setLayout(new BorderLayout());
        this.add(pane, BorderLayout.CENTER);
        this.add(field, BorderLayout.SOUTH);
    }
    public void append(String text) {
        synchronized(LOCK) {
            area.append(text);
        }
    } 
    private class FieldListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            handleTextEnter();
        }
    }
    
    private void handleTextEnter() {
            String text = field.getText();
            field.setText("");
            if (text.trim().isEmpty())
                return;
            synchronized(LOCK) {
                if (running) {
                    try {
                    append("\n[APP]> " + text);
                        outputStream.write(text.getBytes());
                        outputStream.flush();
                    }
                    catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                else {
                    append("\n" + dir.getPath() + "> " + text);
                    handle(text);
                }
            }
    }
    
    public void handle(String command) {
        String[] split = command.split(" ");
        if (split.length > 0 && commands.containsKey(split[0].toLowerCase())) {
            commands.get(split[0].toLowerCase()).invoke(split);
            return;
        }
        try {
            Process process = Runtime.getRuntime().exec(command, null, dir);
            synchronized(LOCK) {
                inputStreamThread = startParsingThread(process.getInputStream(), true);
                errorStreamThread = startParsingThread(process.getErrorStream(), false);
                outputStream = process.getOutputStream();
            }
            running = true;
        }
        catch (Exception e) {
            e.printStackTrace();
            append("\n\n" + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }
    private Thread startParsingThread(final InputStream stream, final boolean trigger) {
        Thread thread = new Thread(new Runnable() {
                public void run() {
                    DataInputStream input = new DataInputStream(stream);
                    try {
                        try {
                            while (true) {
                                append((char) input.readByte() + "");
                            }
                        }
                        catch (EOFException e) {
                            if (trigger)
                                System.out.println("Reached end of stream, assuming application/command exit!");
                        }
                        if (trigger) {
                            Thread.sleep(100);
                            append("\n\t\t[Application exit]");
                            running = false;
                        }
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
    private interface CommandListener {
        public void invoke(String args[]);
    }
}
