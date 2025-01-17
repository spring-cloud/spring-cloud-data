#!/usr/bin/env bash
if [ -z "$BASH_VERSION" ]; then
    echo "This script requires Bash. Use: bash $0 $*"
    exit 1
fi
SCDIR=$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")
if [ "$DEBUG" == "true" ]; then
    echo "DEBUG: configure-database.sh $*"
fi
if [ "$4" = "" ]; then
    echo "<app> <database> <url> <username> <password>"
    echo " OR"
    echo "<app> <database> <url> <secret-name> [secret-username-key] [secret-password-key]"
    echo " - app: dataflow|skipper|global"
    echo " - secret-username-key: key name in secret. The default is username"
    echo " - secret-password-key: key name in secret. The default is password"
    echo "  If username / password is provided it will be assigned to the values file."
    exit 1
fi

case $1 in
"dataflow")
    APP=server
    ;;
"skipper")
    APP=skipper
    ;;
"global")
    APP=global
    ;;
*)
    echo "Invalid application: $1"
    exit 1
    ;;
esac
if [ "$DEBUG" == "true" ]; then
    echo "DEBUG: APP=$APP"
fi
case $2 in
"postgresql" | "postgres")
    DATABASE=postgresql
    ;;
"mariadb" | "maria")
    DATABASE=mariadb
    ;;
"mysql57")
    DATABASE=mysql57
    ;;
"oracle")
    DATABASE=oracle
    ;;
*)
    echo "Unsupported or invalid database $2"
    exit 1
    ;;
esac
set +e
export JDBC_URL="$3"
if [ "$APP" = "global" ]; then
    yq '.configuration.database.url = strenv(JDBC_URL)' -i ./scdf-helm-values.yml
else
    yq ".${APP}.config.database.url = strenv(JDBC_URL)" -i ./scdf-helm-values.yml
fi

if [ "$DEBUG" == "true" ]; then
    echo "DEBUG: DATABASE=$DATABASE"
fi
case $DATABASE in
"mariadb" | "mysql57")
    JDBC_DRIVER_CLASS=org.mariadb.jdbc.Driver
    ;;
"postgresql")
    JDBC_DRIVER_CLASS=org.postgresql.Driver
    ;;
"oracle")
    JDBC_DRIVER_CLASS=oracle.jdbc.OracleDriver
    ;;
*)
    echo "Unsupported $DATABASE."
    ;;
esac
if [ "$DEBUG" == "true" ]; then
    echo "DEBUG: JDBC_DRIVER_CLASS=$JDBC_DRIVER_CLASS"
fi

if [ "$JDBC_DRIVER_CLASS" != "" ]; then
    export JDBC_DRIVER_CLASS
    if [ "$APP" = "global" ]; then
        yq '.configuration.database.driverClassName = strenv(JDBC_DRIVER_CLASS)' -i ./scdf-helm-values.yml
    else
        yq ".${APP}.config.database.driverClassName = strenv(JDBC_DRIVER_CLASS)" -i ./scdf-helm-values.yml
    fi
fi

if [ "$DIALECT" = "" ] && [ "$DATABASE" = "mariadb" ]; then
    DIALECT="org.hibernate.dialect.MariaDB106Dialect"
fi
if [ "$DIALECT" != "" ]; then
    if [ "$DEBUG" == "true" ]; then
        echo "DEBUG: DIALECT=$DIALECT"
    fi
    export DIALECT
    if [ "$APP" = "global" ]; then
        yq '.configuration.database.dialect = strenv(DIALECT)' -i ./scdf-helm-values.yml
    else
        yq ".${APP}.config.database.dialect = strenv(DIALECT)" -i ./scdf-helm-values.yml
    fi
fi
if [ "$6" != "" ]; then
    SECRET_NAME=$4
    SECRET_USERNAME_KEY="$5"
    SECRET_PASSWORD_KEY="$6"
elif [ "$5" != "" ]; then
    USERNAME="$4"
    PASSWORD="$5"
else
    SECRET_NAME=$4
    SECRET_USERNAME_KEY=username
    SECRET_PASSWORD_KEY=password
fi
if [ "$SECRET_NAME" != "" ]; then
    if [ "$DEBUG" == "true" ]; then
        echo "DEBUG: SECRET_NAME=$SECRET_NAME, SECRET_USERNAME_KEY=$SECRET_USERNAME_KEY, SECRET_PASSWORD_KEY=$SECRET_PASSWORD_KEY"
    fi
    export SECRET_NAME
    export SECRET_USERNAME_KEY
    export SECRET_PASSWORD_KEY
    if [ "$APP" = "global" ]; then
        yq '.configuration.database.usernameSecret.name = strenv(SECRET_NAME)' -i ./scdf-helm-values.yml
        yq '.configuration.database.usernameSecret.key = strenv(SECRET_USERNAME_KEY)' -i ./scdf-helm-values.yml
        yq '.configuration.database.passwordSecret.name = strenv(SECRET_NAME)' -i ./scdf-helm-values.yml
        yq '.configuration.database.passwordSecret.key = strenv(SECRET_PASSWORD_KEY)' -i ./scdf-helm-values.yml
    else
        yq ".${APP}.config.database.usernameSecret.name = strenv(SECRET_NAME)" -i ./scdf-helm-values.yml
        yq ".${APP}.config.database.usernameSecret.key = strenv(SECRET_USERNAME_KEY)" -i ./scdf-helm-values.yml
        yq ".${APP}.config.database.passwordSecret.name = strenv(SECRET_NAME)" -i ./scdf-helm-values.yml
        yq ".${APP}.config.database.passwordSecret.key = strenv(SECRET_PASSWORD_KEY)" -i ./scdf-helm-values.yml
    fi
else
    if [ "$USERNAME" = "" ]; then
        echo "Expected $USERNAME"
        exit 1
    fi
    if [ "$PASSWORD" = "" ]; then
        echo "Expected $PASSWORD"
        exit 1
    fi
    export USERNAME
    export PASSWORD
    if [ "$APP" = "global" ]; then
        yq '.configuration.database.username = strenv(USERNAME)' -i ./scdf-helm-values.yml
        yq '.configuration.database.password = strenv(PASSWORD)' -i ./scdf-helm-values.yml
    else
        yq ".${APP}.config.database.username = strenv(USERNAME)" -i ./scdf-helm-values.yml
        yq ".${APP}.config.database.password = strenv(PASSWORD)" -i ./scdf-helm-values.yml

    fi
fi

echo "Set ${APP} JDBC url: $JDBC_URL"
echo "Set ${APP} JDBC class: $JDBC_DRIVER_CLASS"
echo "Configured ${APP} $DATABASE"
