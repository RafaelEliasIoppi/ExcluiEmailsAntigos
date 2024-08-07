package email;

import javax.mail.*;
import javax.mail.Flags.Flag;
import javax.swing.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.*;

public class EmailManager {

    private Properties props;
    private String username;
    private String password;
    private int totalEmails;
    private int emailsParaExcluir;
    private int emailsMovidos;

    public EmailManager() {
        carregarPropriedades();
    }

    private void carregarPropriedades() {
        String[] options = {"email.properties (Usar arquivo email)", "email1.properties (Usar arquivo email1)"};
        String selectedOption = (String) JOptionPane.showInputDialog(null, "Selecione o arquivo de propriedades:", "Escolha do Arquivo",
                JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

        String propertiesFilePath;
        if ("email.properties (Usar arquivo email)".equals(selectedOption)) {
            propertiesFilePath = "C:\\projeto\\arquivos\\email.properties";
        } else {
            propertiesFilePath = "C:\\projeto\\arquivos\\email1.properties";
        }

        props = new Properties();
        try (FileInputStream fis = new FileInputStream(propertiesFilePath)) {
            props.load(fis);
            username = props.getProperty("mail.username");
            password = props.getProperty("mail.password");

            props.put("mail.store.protocol", "imaps");
            props.put("mail.imap.host", "imap.gmail.com");
            props.put("mail.imap.port", "993");
            props.put("mail.imap.ssl.enable", "true");
        } catch (IOException e) {
            throw new RuntimeException("Erro ao carregar arquivo de propriedades", e);
        }
    }

    public String getUsername() {
        return username;
    }

    public List<String> listarPastas() {
        List<String> pastas = new ArrayList<>();
        try (Store store = getSession().getStore("imaps")) {
            store.connect("imap.gmail.com", username, password);
            fetchFolders(store.getDefaultFolder(), pastas);
        } catch (MessagingException e) {
            throw new RuntimeException("Erro ao conectar e listar pastas", e);
        }
        return pastas;
    }

    private void fetchFolders(Folder folder, List<String> pastas) throws MessagingException {
        pastas.add(folder.getFullName());
        for (Folder f : folder.list()) {
            fetchFolders(f, pastas);
        }
    }

    public void moverEmails(String pastaSelecionada, int dias, JTextArea textArea) {
        try (Store store = getSession().getStore("imaps")) {
            store.connect("imap.gmail.com", username, password);

            Folder pasta = store.getFolder(pastaSelecionada);
            pasta.open(Folder.READ_WRITE);

            Date dataLimite = new Date(System.currentTimeMillis() - (dias * 24 * 60 * 60 * 1000L));
            Message[] mensagens = pasta.getMessages();
            totalEmails = mensagens.length;

            informarStatusInicial(pastaSelecionada, totalEmails, textArea);

            List<Message> mensagensParaExcluir = new ArrayList<>();
            for (Message mensagem : mensagens) {
                if (mensagem.getReceivedDate().before(dataLimite)) {
                    mensagensParaExcluir.add(mensagem);
                }
            }

            emailsParaExcluir = mensagensParaExcluir.size();
            relatorioIntermediario(emailsParaExcluir, textArea, dias);

            Folder lixeira = store.getFolder("[Gmail]/Lixeira");
            if (!lixeira.exists()) {
                lixeira.create(Folder.HOLDS_MESSAGES);
            }
            lixeira.open(Folder.READ_WRITE);

            moverEExcluirEmailsPorLotes(mensagensParaExcluir, pasta, lixeira, textArea);

            pasta.close(true);
            lixeira.close(true);

        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }

    private void moverEExcluirEmailsPorLotes(List<Message> mensagensParaExcluir, Folder pasta, Folder lixeira, JTextArea textArea) throws MessagingException {
        ExecutorService executor = Executors.newFixedThreadPool(10); // Ajuste conforme necessário
        List<Message[]> batches = dividirEmLotes(mensagensParaExcluir, 100); // Ajuste o tamanho do lote conforme necessário
        CountDownLatch latch = new CountDownLatch(batches.size());

        try {
            for (int i = 0; i < batches.size(); i++) {
                Message[] batch = batches.get(i);
                int batchNumber = i + 1; // Numero do lote atual
                
                executor.submit(() -> {
                    try {
                        pasta.copyMessages(batch, lixeira);
                        esvaziarLixeira(lixeira);
                        // Atualizar o JTextArea com o progresso
                        SwingUtilities.invokeLater(() -> {
                            textArea.append("Lote " + batchNumber + " movido e excluído da lixeira.\n");
                            textArea.setCaretPosition(textArea.getDocument().getLength());
                        });
                    } catch (MessagingException e) {
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            executor.shutdown();
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            emailsMovidos = mensagensParaExcluir.size();
        } finally {
            if (!executor.isTerminated()) {
                executor.shutdownNow();
            }
        }
    }

    private List<Message[]> dividirEmLotes(List<Message> mensagens, int tamanhoLote) {
        List<Message[]> batches = new ArrayList<>();
        for (int i = 0; i < mensagens.size(); i += tamanhoLote) {
            int fim = Math.min(mensagens.size(), i + tamanhoLote);
            batches.add(mensagens.subList(i, fim).toArray(new Message[0]));
        }
        return batches;
    }

    private void esvaziarLixeira(Folder lixeira) {
        try {
            Message[] mensagens = lixeira.getMessages();
            for (Message mensagem : mensagens) {
                mensagem.setFlag(Flag.DELETED, true);
            }
            lixeira.expunge();
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }

    private void informarStatusInicial(String nomePasta, int totalEmails, JTextArea textArea) {
        SwingUtilities.invokeLater(() -> {
            textArea.append("Total de emails encontrados na pasta " + nomePasta + ": " + totalEmails + "\n");
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }

    private void relatorioIntermediario(int totalEmailsParaExcluir, JTextArea textArea, int dias) {
        SwingUtilities.invokeLater(() -> {
            textArea.append("Total de e-mails selecionados para exclusão, mais antigos que " + dias + " dias: " + totalEmailsParaExcluir + "\n");
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }

    public void mostrarRelatorioFinal() {
        SwingUtilities.invokeLater(() -> {
            String mensagem = String.format(
                "Total de emails na pasta selecionada: %d\nTotal de e-mails movidos para a lixeira: %d\nTotal de e-mails removidos definitivamente: %d\n",
                totalEmails, emailsMovidos, emailsParaExcluir);
            
            JOptionPane.showMessageDialog(null, mensagem, "Relatório Final", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private Session getSession() {
        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
    }
}
