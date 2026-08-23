update app_user set version = 0 where version is null;
alter table app_user modify version bigint default 0;