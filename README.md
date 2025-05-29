# ExcluiEmailsAntigos

Deve ser criado o arquivo email.properties que deve conter a seguinte configuração:
# Credenciais do usuário
mail.username=SEU_EMAIL_AQUI
mail.password=SUA_SENHA_AQUI

# Configuração do servidor IMAP
mail.store.protocol=imaps
mail.imap.host=imap.gmail.com
mail.imap.port=993
mail.imap.ssl.enable=true

# Configuração opcional para SMTP (envio de emails, caso necessário)
mail.smtp.host=smtp.gmail.com
mail.smtp.port=587
mail.smtp.auth=true
mail.smtp.starttls.enable=true
