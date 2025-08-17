import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.layout.StackPane;

public class ToDoListFXAdvancedApp extends JFrame {

    private DefaultListModel<Task> taskModel;
    private JList<Task> taskList;
    private JTextField taskInput;
    private JComboBox<String> categoryBox;
    private JComboBox<String> filterBox;
    private JSpinner dueDateSpinner;
    private JButton addBtn, editBtn, deleteBtn, completeBtn, saveBtn, loadBtn;
    private JFXPanel fxPanel;

    private final TaskService service = new TaskService();

    public ToDoListFXAdvancedApp() {
        setTitle("Advanced To-Do List App");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 500));
        setLocationRelativeTo(null);

        initUI();
        SwingUtilities.invokeLater(loadBtn::doClick);
    }

    private void initUI() {
        var root = new JPanel(new BorderLayout(12,12));
        root.setBorder(new EmptyBorder(12,12,12,12));
        setContentPane(root);

        // Top panel: Task input
        var top = new JPanel();
        taskInput = new JTextField(15);
        categoryBox = new JComboBox<>(new String[]{"Work","Personal","Study","Other"});
        dueDateSpinner = new JSpinner(new SpinnerDateModel());
        ((JSpinner.DateEditor)dueDateSpinner.getEditor()).getFormat().applyPattern("yyyy-MM-dd");
        addBtn = new JButton("Add Task");
        top.add(new JLabel("Task:"));
        top.add(taskInput);
        top.add(new JLabel("Category:"));
        top.add(categoryBox);
        top.add(new JLabel("Due Date:"));
        top.add(dueDateSpinner);
        top.add(addBtn);
        root.add(top, BorderLayout.NORTH);

        // Split pane: left list, right chart
        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setResizeWeight(0.5);
        root.add(split, BorderLayout.CENTER);

        // Left panel: task list + action buttons + filter
        var left = new JPanel(new BorderLayout(8,8));
        taskModel = new DefaultListModel<>();
        taskList = new JList<>(taskModel);
        taskList.setCellRenderer(new TaskRenderer());
        JScrollPane scrollPane = new JScrollPane(taskList);
        left.add(scrollPane, BorderLayout.CENTER);

        var btnPanel = new JPanel();
        editBtn = new JButton("Edit");
        completeBtn = new JButton("Complete");
        deleteBtn = new JButton("Delete");
        saveBtn = new JButton("Save");
        loadBtn = new JButton("Load");
        filterBox = new JComboBox<>(new String[]{"All","Pending","Completed"});
        btnPanel.add(editBtn);
        btnPanel.add(completeBtn);
        btnPanel.add(deleteBtn);
        btnPanel.add(saveBtn);
        btnPanel.add(loadBtn);
        btnPanel.add(new JLabel("Filter:"));
        btnPanel.add(filterBox);
        left.add(btnPanel, BorderLayout.SOUTH);

        split.setLeftComponent(left);

        // Right panel: JavaFX chart
        fxPanel = new JFXPanel();
        split.setRightComponent(fxPanel);

        // Action listeners
        addBtn.addActionListener(e -> addTask());
        editBtn.addActionListener(e -> editTask());
        completeBtn.addActionListener(e -> completeTask());
        deleteBtn.addActionListener(e -> deleteTask());
        saveBtn.addActionListener(e -> service.saveTasks(taskModel.elements()));
        loadBtn.addActionListener(e -> loadTasks());
        filterBox.addActionListener(e -> applyFilter());

        taskModel.addListDataListener(e -> updateChart());
    }

    private void addTask() {
        String text = taskInput.getText().trim();
        if (text.isEmpty()) return;
        String category = (String) categoryBox.getSelectedItem();
        LocalDate dueDate = LocalDate.ofInstant(((Date)dueDateSpinner.getValue()).toInstant(), ZoneId.systemDefault());
        Task t = new Task(text, category, dueDate);
        taskModel.addElement(t);
        taskInput.setText("");
        updateChart();
    }

    private void editTask() {
        int idx = taskList.getSelectedIndex();
        if (idx != -1) {
            Task t = taskModel.get(idx);
            String newText = JOptionPane.showInputDialog(this, "Edit Task:", t.text);
            if (newText != null && !newText.trim().isEmpty()) {
                t.text = newText.trim();
            }
            String newCategory = (String) JOptionPane.showInputDialog(this, "Select Category:", "Edit Category", JOptionPane.PLAIN_MESSAGE, null, new String[]{"Work","Personal","Study","Other"}, t.category);
            if (newCategory != null) t.category = newCategory;
            LocalDate newDue = LocalDate.ofInstant(((Date)dueDateSpinner.getValue()).toInstant(), ZoneId.systemDefault());
            t.dueDate = newDue;
            taskList.repaint();
            updateChart();
        }
    }

    private void completeTask() {
        int idx = taskList.getSelectedIndex();
        if (idx != -1) {
            Task t = taskModel.get(idx);
            t.completed = !t.completed;
            taskList.repaint();
            updateChart();
        }
    }

    private void deleteTask() {
        int idx = taskList.getSelectedIndex();
        if (idx != -1) {
            taskModel.remove(idx);
            updateChart();
        }
    }

    private void loadTasks() {
        List<Task> tasks = service.loadTasks();
        taskModel.clear();
        for (Task t : tasks) taskModel.addElement(t);
        updateChart();
    }

    private void applyFilter() {
        String filter = (String) filterBox.getSelectedItem();
        if (filter == null) return;

        for (int i=0;i<taskModel.size();i++) {
            Task t = taskModel.get(i);
            if (filter.equals("All")) taskList.ensureIndexIsVisible(i);
            else if (filter.equals("Pending") && t.completed) taskList.removeSelectionInterval(i,i);
            else if (filter.equals("Completed") && !t.completed) taskList.removeSelectionInterval(i,i);
        }
        taskList.repaint();
    }

    private void updateChart() {
        Map<LocalDate, Integer> completedPerDay = new TreeMap<>();
        for (int i=0;i<taskModel.size();i++){
            Task t = taskModel.get(i);
            if (t.completed){
                completedPerDay.put(t.dueDate, completedPerDay.getOrDefault(t.dueDate,0)+1);
            }
        }

        Platform.runLater(() -> {
            CategoryAxis xAxis = new CategoryAxis();
            NumberAxis yAxis = new NumberAxis();
            xAxis.setLabel("Due Date");
            yAxis.setLabel("Completed Tasks");

            BarChart<String,Number> barChart = new BarChart<>(xAxis,yAxis);
            barChart.setTitle("Completed Tasks per Day");
            XYChart.Series<String,Number> series = new XYChart.Series<>();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM dd");
            for (var entry : completedPerDay.entrySet()) {
                series.getData().add(new XYChart.Data<>(entry.getKey().format(fmt), entry.getValue()));
            }
            barChart.getData().clear();
            barChart.getData().add(series);
            fxPanel.setScene(new Scene(new StackPane(barChart), 400, 400));
        });
    }

    // --- Task model ---
    static class Task implements Serializable {
        String text;
        boolean completed;
        String category;
        LocalDate dueDate;
        LocalDateTime createdAt;

        Task(String text, String category, LocalDate dueDate){
            this.text=text;
            this.category=category;
            this.dueDate=dueDate;
            completed=false;
            createdAt=LocalDateTime.now();
        }

        @Override
        public String toString(){ return text; }
    }

    // --- Custom Renderer ---
    static class TaskRenderer extends JLabel implements ListCellRenderer<Task> {
        @Override
        public Component getListCellRendererComponent(JList<? extends Task> list, Task value, int index, boolean isSelected, boolean cellHasFocus) {
            setText(String.format("%s [%s] (Due: %s)", value.text, value.category, value.dueDate));
            if (value.completed) setText("[✔] " + getText());
            setOpaque(true);
            setBackground(isSelected?Color.LIGHT_GRAY:Color.WHITE);
            return this;
        }
    }

    // --- Task service for save/load ---
    static class TaskService {
        private final File file = new File("tasks_advanced.dat");

        public void saveTasks(Enumeration<Task> tasks) {
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
                List<Task> list = Collections.list(tasks);
                oos.writeObject(list);
                JOptionPane.showMessageDialog(null, "Tasks saved!");
            } catch (IOException e) { JOptionPane.showMessageDialog(null,"Failed to save tasks."); }
        }

        public List<Task> loadTasks() {
            if (!file.exists()) return new ArrayList<>();
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                return (List<Task>) ois.readObject();
            } catch (Exception e) { JOptionPane.showMessageDialog(null,"Failed to load tasks."); return new ArrayList<>(); }
        }
    }

    public static void main(String[] args){
        SwingUtilities.invokeLater(() -> new ToDoListFXAdvancedApp().setVisible(true));
    }
}
