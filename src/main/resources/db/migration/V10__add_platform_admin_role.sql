insert into role(version, created_at, updated_at, name, description)
select 0, current_timestamp, current_timestamp, 'ROLE_PLATFORM_ADMIN', 'Administrador de plataforma'
where not exists (select 1 from role where name = 'ROLE_PLATFORM_ADMIN');

-- Bootstrap only the lowest-ID existing administrator, never every tenant admin.
insert into user_roles(user_id, role_id)
select first_admin.user_id, platform_role.id
from (
    select min(u.id) as user_id
    from app_user u
    join user_roles ur on ur.user_id = u.id
    join role r on r.id = ur.role_id
    where r.name = 'ROLE_ADMIN'
) first_admin
join role platform_role on platform_role.name = 'ROLE_PLATFORM_ADMIN'
where first_admin.user_id is not null
    and not exists (
        select 1 from user_roles ur
        join role r on r.id = ur.role_id
        where r.name = 'ROLE_PLATFORM_ADMIN'
    );
