CREATE TABLE IF NOT EXISTS estado (
	id INT AUTO_INCREMENT  PRIMARY KEY,
	nome varchar(100) NOT NULL,
	sigla varchar(2) NOT NULL,
	ts_data_hora_cadastro timestamp NULL,
	ts_data_hora_ultima_atualizacao timestamp NULL,
	CONSTRAINT estado_pkey PRIMARY KEY (id)
);