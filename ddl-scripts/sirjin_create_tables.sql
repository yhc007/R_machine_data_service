CREATE TABLE pgm_history_data (
                                       shop_id varchar NOT NULL,
                                       nc_id varchar NOT NULL,
                                       pgm_nm varchar NOT NULL,
                                       start_date timestamp NULL,
                                       end_date timestamp NULL
);
CREATE UNIQUE INDEX pgm_history_data_shop_id_idx ON pgm_history_data (shop_id,nc_id,pgm_nm,start_date);




CREATE TABLE alarm_data (
                                 shop_id varchar NOT NULL,
                                 nc_id varchar NOT NULL,
                                 start_date timestamp NULL,
                                 end_date timestamp NULL,
                                 alarm_code varchar NULL,
                                 alarm_message varchar NULL,
                                 is_fixed boolean NULL
);
CREATE UNIQUE INDEX alarm_data_shop_id_idx ON alarm_data (shop_id,nc_id,start_date,alarm_code);


CREATE TABLE public.time_chart_data (
                                      shop_id varchar NOT NULL,
                                      nc_id varchar NOT NULL,
                                      "date" date NULL,
                                      statue varchar NULL,
                                      reg_date timestamp NULL
);
CREATE UNIQUE INDEX time_chart_data_shop_id_idx ON public.time_chart_data (shop_id,nc_id,reg_date);


CREATE TABLE public.summary_data (
                                   shop_id int NOT NULL,
                                   "date" date NULL,
                                   nc_id varchar NOT NULL,
                                   part_count int NULL,
                                   cutting_time int NULL,
                                   in_cycle_time int NULL,
                                   wait_time int NULL,
                                   alarm_time int NULL,
                                   no_connection_time int NULL,
                                   operation_rate float8 NULL
);
CREATE UNIQUE INDEX summary_data_shop_id_idx ON public.summary_data (shop_id,"date",nc_id);
