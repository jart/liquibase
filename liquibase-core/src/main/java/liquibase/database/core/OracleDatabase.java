package liquibase.database.core;

import liquibase.CatalogAndSchema;
import liquibase.database.AbstractJdbcDatabase;
import liquibase.database.DatabaseConnection;
import liquibase.database.OfflineConnection;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.exception.UnexpectedLiquibaseException;
import liquibase.exception.ValidationErrors;
import liquibase.executor.ExecutorService;
import liquibase.logging.LogFactory;
import liquibase.statement.*;
import liquibase.statement.core.RawCallStatement;
import liquibase.statement.core.RawSqlStatement;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.*;
import liquibase.util.JdbcUtils;
import liquibase.util.StringUtils;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Encapsulates Oracle database support.
 */
public class OracleDatabase extends AbstractJdbcDatabase {
    public static final String PRODUCT_NAME = "oracle";


    private Set<String> reservedWords = new HashSet<String>();
    private Set<String> userDefinedTypes = null;

    private Boolean canAccessDbaRecycleBin;

    public OracleDatabase() {
        super.unquotedObjectsAreUppercased=true;
        super.setCurrentDateTimeFunction("SYSTIMESTAMP");
        // Setting list of Oracle's native functions
        dateFunctions.add(new DatabaseFunction("SYSDATE"));
        dateFunctions.add(new DatabaseFunction("SYSTIMESTAMP"));
        dateFunctions.add(new DatabaseFunction("CURRENT_TIMESTAMP"));
        super.sequenceNextValueFunction = "%s.nextval";
        super.sequenceCurrentValueFunction = "%s.currval";
    }

    @Override
    public int getPriority() {
        return PRIORITY_DEFAULT;
    }

    @Override
    public void setConnection(DatabaseConnection conn) {
        reservedWords.addAll(Arrays.asList("GROUP", "USER", "SESSION", "PASSWORD", "RESOURCE", "START", "SIZE", "UID", "DESC")); //more reserved words not returned by driver

        Connection sqlConn = null;
        if (!(conn instanceof OfflineConnection)) {
            try {
                /**
                 * Don't try to call getWrappedConnection if the conn instance is
                 * is not a JdbcConnection. This happens for OfflineConnection.
                 * @see <a href="https://liquibase.jira.com/browse/CORE-2192">CORE-2192</a>
                 **/
                if (conn instanceof JdbcConnection) {
                    Method wrappedConn = conn.getClass().getMethod("getWrappedConnection");
                    wrappedConn.setAccessible(true);
                    sqlConn = (Connection) wrappedConn.invoke(conn);
                }
            } catch (Exception e) {
                throw new UnexpectedLiquibaseException(e);
            }

            if (sqlConn != null) {
                try {
                    reservedWords.addAll(Arrays.asList(sqlConn.getMetaData().getSQLKeywords().toUpperCase().split(",\\s*")));
                } catch (SQLException e) {
                    LogFactory.getLogger().info("Could get sql keywords on OracleDatabase: " + e.getMessage());
                    //can not get keywords. Continue on
                }
                try {
                    Method method = sqlConn.getClass().getMethod("setRemarksReporting", Boolean.TYPE);
                    method.setAccessible(true);
                    method.invoke(sqlConn, true);
                } catch (Exception e) {
                    LogFactory.getLogger().info("Could not set remarks reporting on OracleDatabase: " + e.getMessage());
                    ; //cannot set it. That is OK
                }

            }
        }
        super.setConnection(conn);
    }

    @Override
    public String getShortName() {
        return "oracle";
    }

    @Override
    protected String getDefaultDatabaseProductName() {
        return "Oracle";
    }

    @Override
    public Integer getDefaultPort() {
        return 1521;
    }

    @Override
    public String getJdbcCatalogName(CatalogAndSchema schema) {
        return null;
    }

    @Override
    public String getJdbcSchemaName(CatalogAndSchema schema) {
        return correctObjectName(schema.getCatalogName() == null ? schema.getSchemaName() : schema.getCatalogName(), Schema.class);
    }

    @Override
    public String generatePrimaryKeyName(String tableName) {
        if (tableName.length() > 27) {
            return "PK_" + tableName.toUpperCase().substring(0, 27);
        } else {
            return "PK_" + tableName.toUpperCase();
        }
    }

    @Override
    public boolean supportsInitiallyDeferrableColumns() {
        return true;
    }

    @Override
    public boolean isReservedWord(String objectName) {
        return reservedWords.contains(objectName.toUpperCase());
    }

    @Override
    public boolean supportsSequences() {
        return true;
    }

    /**
     * Oracle supports catalogs in liquibase terms
     *
     * @return
     */
    @Override
    public boolean supportsSchemas() {
        return false;
    }

    @Override
    protected String getConnectionCatalogName() throws DatabaseException {
        if (getConnection() instanceof OfflineConnection) {
            return getConnection().getCatalog();
        }
        try {
            return ExecutorService.getInstance().getExecutor(this).queryForObject(new RawCallStatement("select sys_context( 'userenv', 'current_schema' ) from dual"), String.class);
        } catch (Exception e) {
            LogFactory.getLogger().info("Error getting default schema", e);
        }
        return null;
    }

    @Override
    public boolean isCorrectDatabaseImplementation(DatabaseConnection conn) throws DatabaseException {
        return PRODUCT_NAME.equalsIgnoreCase(conn.getDatabaseProductName());
    }

    @Override
    public String getDefaultDriver(String url) {
        if (url.startsWith("jdbc:oracle")) {
            return "oracle.jdbc.OracleDriver";
        }
        return null;
    }

    @Override
    public String getDefaultCatalogName() {//NOPMD
        return super.getDefaultCatalogName() == null ? null : super.getDefaultCatalogName().toUpperCase();
    }

    /**
     * Return an Oracle date literal with the same value as a string formatted using ISO 8601.
     * <p/>
     * Convert an ISO8601 date string to one of the following results:
     * to_date('1995-05-23', 'YYYY-MM-DD')
     * to_date('1995-05-23 09:23:59', 'YYYY-MM-DD HH24:MI:SS')
     * <p/>
     * Implementation restriction:
     * Currently, only the following subsets of ISO8601 are supported:
     * YYYY-MM-DD
     * YYYY-MM-DDThh:mm:ss
     */
    @Override
    public String getDateLiteral(String isoDate) {
        String normalLiteral = super.getDateLiteral(isoDate);

        if (isDateOnly(isoDate)) {
            StringBuffer val = new StringBuffer();
            val.append("to_date(");
            val.append(normalLiteral);
            val.append(", 'YYYY-MM-DD')");
            return val.toString();
        } else if (isTimeOnly(isoDate)) {
            StringBuffer val = new StringBuffer();
            val.append("to_date(");
            val.append(normalLiteral);
            val.append(", 'HH24:MI:SS')");
            return val.toString();
        } else if (isTimestamp(isoDate)) {
            StringBuffer val = new StringBuffer(26);
            val.append("to_timestamp(");
            val.append(normalLiteral);
            val.append(", 'YYYY-MM-DD HH24:MI:SS.FF')");
            return val.toString();
        } else if (isDateTime(isoDate)) {
            normalLiteral = normalLiteral.substring(0, normalLiteral.lastIndexOf('.')) + "'";

            StringBuffer val = new StringBuffer(26);
            val.append("to_date(");
            val.append(normalLiteral);
            val.append(", 'YYYY-MM-DD HH24:MI:SS')");
            return val.toString();
        } else {
            return "UNSUPPORTED:" + isoDate;
        }
    }

    @Override
    public boolean isSystemObject(DatabaseObject example) {
        if (example == null) {
            return false;
        }

        if (this.isLiquibaseObject(example)) {
            return false;
        }

        if (example instanceof Schema) {
            if ("SYSTEM".equals(example.getName()) || "SYS".equals(example.getName()) || "CTXSYS".equals(example.getName())|| "XDB".equals(example.getName())) {
                return true;
            }
            if ("SYSTEM".equals(example.getSchema().getCatalogName()) || "SYS".equals(example.getSchema().getCatalogName()) || "CTXSYS".equals(example.getSchema().getCatalogName()) || "XDB".equals(example.getSchema().getCatalogName())) {
                return true;
            }
        } else if (isSystemObject(example.getSchema())) {
            return true;
        }
        if (example instanceof Catalog) {
            if (("SYSTEM".equals(example.getName()) || "SYS".equals(example.getName()) || "CTXSYS".equals(example.getName()) || "XDB".equals(example.getName()))) {
                return true;
            }
        } else if (example.getName() != null) {
            if (example.getName().startsWith("BIN$")) { //oracle deleted table
                boolean filteredInOriginalQuery = this.canAccessDbaRecycleBin();
                if (!filteredInOriginalQuery) {
                    filteredInOriginalQuery = StringUtils.trimToEmpty(example.getSchema().getName()).equalsIgnoreCase(this.getConnection().getConnectionUserName());
                }

                if (filteredInOriginalQuery) {
                    if (example instanceof PrimaryKey || example instanceof Index || example instanceof liquibase.statement.UniqueConstraint) { //some objects don't get renamed back and so are already filtered in the metadata queries
                        return false;
                    } else {
                        return true;
                    }
                } else {
                    return true;
                }
            } else if (example.getName().startsWith("AQ$")) { //oracle AQ tables
                return true;
            } else if (example.getName().startsWith("DR$")) { //oracle index tables
                return true;
            } else if (example.getName().startsWith("SYS_IOT_OVER")) { //oracle system table
                return true;
            } else if ((example.getName().startsWith("MDRT_") || example.getName().startsWith("MDRS_")) && example.getName().endsWith("$")) {
                // CORE-1768 - Oracle creates these for spatial indices and will remove them when the index is removed.
                return true;
            } else if (example.getName().startsWith("MLOG$_")) { //Created by materliaized view logs for every table that is part of a materialized view. Not available for DDL operations.
                return true;
            } else if (example.getName().startsWith("RUPD$_")) { //Created by materialized view log tables using primary keys. Not available for DDL operations.
                return true;
            } else if (example.getName().startsWith("WM$_")) { //Workspace Manager backup tables.
                return true;
            } else if (example.getName().equals("CREATE$JAVA$LOB$TABLE")) { //This table contains the name of the Java object, the date it was loaded, and has a BLOB column to store the Java object.
                return true;
            } else if (example.getName().equals("JAVA$CLASS$MD5$TABLE")) { //This is a hash table that tracks the loading of Java objects into a schema.
                return true;
            } else if (example.getName().startsWith("ISEQ$$_")) { //System-generated sequence
                return true;
            }
        }

        return super.isSystemObject(example);
    }

    @Override
    public boolean supportsTablespaces() {
        return true;
    }

    @Override
    public boolean supportsAutoIncrement() {
        // Oracle supports Identity beginning with version 12c
        boolean isAutoIncrementSupported = false;

        try {
            if (getDatabaseMajorVersion() >= 12) {
                isAutoIncrementSupported = true;
            }

            // Returning true will generate create table command with 'IDENTITY' clause, example:
            // CREATE TABLE AutoIncTest (IDPrimaryKey NUMBER(19) GENERATED BY DEFAULT AS IDENTITY NOT NULL, TypeID NUMBER(3) NOT NULL, Description NVARCHAR2(50), CONSTRAINT PK_AutoIncTest PRIMARY KEY (IDPrimaryKey));

            // While returning false will continue to generate create table command without 'IDENTITY' clause, example:
            // CREATE TABLE AutoIncTest (IDPrimaryKey NUMBER(19) NOT NULL, TypeID NUMBER(3) NOT NULL, Description NVARCHAR2(50), CONSTRAINT PK_AutoIncTest PRIMARY KEY (IDPrimaryKey));

        } catch (DatabaseException ex) {
            isAutoIncrementSupported = false;
        }

        return isAutoIncrementSupported;
    }


//    public Set<UniqueConstraint> findUniqueConstraints(String schema) throws DatabaseException {
//        Set<UniqueConstraint> returnSet = new HashSet<UniqueConstraint>();
//
//        List<Map> maps = new Executor(this).queryForList(new RawSqlStatement("SELECT UC.CONSTRAINT_NAME, UCC.TABLE_NAME, UCC.COLUMN_NAME FROM USER_CONSTRAINTS UC, USER_CONS_COLUMNS UCC WHERE UC.CONSTRAINT_NAME=UCC.CONSTRAINT_NAME AND CONSTRAINT_TYPE='U' ORDER BY UC.CONSTRAINT_NAME"));
//
//        UniqueConstraint constraint = null;
//        for (Map map : maps) {
//            if (constraint == null || !constraint.getName().equals(constraint.getName())) {
//                returnSet.add(constraint);
//                Table table = new Table((String) map.get("TABLE_NAME"));
//                constraint = new UniqueConstraint(map.get("CONSTRAINT_NAME").toString(), table);
//            }
//        }
//        if (constraint != null) {
//            returnSet.add(constraint);
//        }
//
//        return returnSet;
//    }

    @Override
    public boolean supportsRestrictForeignKeys() {
        return false;
    }

    @Override
    public int getDataTypeMaxParameters(String dataTypeName) {
        if (dataTypeName.toUpperCase().equals("BINARY_FLOAT")) {
            return 0;
        }
        if (dataTypeName.toUpperCase().equals("BINARY_DOUBLE")) {
            return 0;
        }
        return super.getDataTypeMaxParameters(dataTypeName);
    }

    @Override
    public boolean jdbcCallsCatalogsSchemas() {
        return true;
    }

    public Set<String> getUserDefinedTypes() {
        if (userDefinedTypes == null) {
            userDefinedTypes = new HashSet<String>();
            if (getConnection() != null && !(getConnection() instanceof OfflineConnection)) {
                try {
                    userDefinedTypes.addAll(ExecutorService.getInstance().getExecutor(this).queryForList(new RawSqlStatement("SELECT TYPE_NAME FROM USER_TYPES"), String.class));
                } catch (DatabaseException e) {
                    //ignore error
                }
            }
        }

        return userDefinedTypes;
    }

    @Override
    public String generateDatabaseFunctionValue(DatabaseFunction databaseFunction) {
        if (databaseFunction != null && databaseFunction.toString().equalsIgnoreCase("current_timestamp")) {
            return databaseFunction.toString();
        }
        if(databaseFunction instanceof SequenceNextValueFunction
                || databaseFunction instanceof SequenceCurrentValueFunction){
            String quotedSeq = super.generateDatabaseFunctionValue(databaseFunction);
            // replace "myschema.my_seq".nextval with "myschema"."my_seq".nextval
            return quotedSeq.replaceFirst("\"([^\\.\"]*)\\.([^\\.\"]*)\"","\"$1\".\"$2\"");

        }

        return super.generateDatabaseFunctionValue(databaseFunction);
    }

    @Override
    public ValidationErrors validate() {
        ValidationErrors errors = super.validate();
        DatabaseConnection connection = getConnection();
        if (connection == null || connection instanceof OfflineConnection) {
            LogFactory.getInstance().getLog().info("Cannot validate offline database");
            return errors;
        }

        if (!canAccessDbaRecycleBin()) {
            errors.addWarning(getDbaRecycleBinWarning());
        }

        return errors;

    }

    public String getDbaRecycleBinWarning() {
        return "Liquibase needs to access the DBA_RECYCLEBIN table so we can automatically handle the case where constraints are deleted and restored. Since Oracle doesn't properly restore the original table names referenced in the constraint, we use the information from the DBA_RECYCLEBIN to automatically correct this issue.\n" +
                "\n" +
                "The user you used to connect to the database ("+getConnection().getConnectionUserName()+") needs to have \"SELECT ON SYS.DBA_RECYCLEBIN\" permissions set before we can perform this operation. Please run the following SQL to set the appropriate permissions, and try running the command again.\n" +
                "\n" +
                "     GRANT SELECT ON SYS.DBA_RECYCLEBIN TO "+getConnection().getConnectionUserName()+";";
    }

    public boolean canAccessDbaRecycleBin() {
        if (canAccessDbaRecycleBin == null) {
            DatabaseConnection connection = getConnection();
            if (connection == null || connection instanceof OfflineConnection) {
                return false;
            }

            Statement statement = null;
            try {
                statement = ((JdbcConnection) connection).createStatement();
                statement.executeQuery("select 1 from dba_recyclebin where 0=1");
                this.canAccessDbaRecycleBin = true;
            } catch (Exception e) {
                if (e instanceof SQLException && e.getMessage().startsWith("ORA-00942")) { //ORA-00942: table or view does not exist
                    this.canAccessDbaRecycleBin = false;
                } else {
                    LogFactory.getInstance().getLog().warning("Cannot check dba_recyclebin access", e);
                    this.canAccessDbaRecycleBin = false;
                }
            } finally {
                JdbcUtils.close(null, statement);
            }
        }

        return canAccessDbaRecycleBin;
    }
}
