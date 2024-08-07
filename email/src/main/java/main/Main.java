package main;

import email.EmailManager;

import javax.swing.*;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            EmailManager emailManager = new EmailManager();
            List<String> pastas = emailManager.listarPastas();
            String pastaSelecionada = (String) JOptionPane.showInputDialog(null, "Selecione uma pasta:", "Pasta",
                    JOptionPane.QUESTION_MESSAGE, null, pastas.toArray(), pastas.get(0));

            if (pastaSelecionada != null) {
                String diasStr = JOptionPane.showInputDialog("Digite o número de dias:");
                try {
                    int dias = Integer.parseInt(diasStr);

                    // Criar e mostrar a GUI rapidamente
                    JDialog dialog = new JDialog();
                    dialog.setTitle("Processando os emails da conta: " + emailManager.getUsername());
                    
                    JTextArea textArea = new JTextArea(20, 40);
                    textArea.setEditable(false);
                    JScrollPane scrollPane = new JScrollPane(textArea);

                    JPanel panel = new JPanel();
                    panel.add(new JLabel("Processando emails..."));
                    panel.add(scrollPane);

                    dialog.getContentPane().add(panel);
                    dialog.pack();
                    dialog.setLocationRelativeTo(null);
                    dialog.setVisible(true);

                    new Thread(() -> {
                        emailManager.moverEmails(pastaSelecionada, dias, textArea);
                        SwingUtilities.invokeLater(() -> {
                            dialog.dispose();
                            emailManager.mostrarRelatorioFinal();
                        });
                    }).start();

                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(null, "Número de dias inválido.", "Erro", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }
}
