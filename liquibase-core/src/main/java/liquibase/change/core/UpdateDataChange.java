package liquibase.change.core;

import liquibase.change.*;
import liquibase.database.Database;
import liquibase.statement.SqlStatement;
import liquibase.statement.core.UpdateStatement;
import liquibase.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@ChangeClass(name = "update", description = "Update Data", priority = ChangeMetaData.PRIORITY_DEFAULT)
public class UpdateDataChange extends AbstractChange implements ChangeWithColumns<ColumnConfig> {

    private String schemaName;
    private String tableName;
    private List<ColumnConfig> columns;

    @TextNode(nodeName="where")
    private String whereClause;

    public UpdateDataChange() {
        columns = new ArrayList<ColumnConfig>();
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = StringUtils.trimToNull(schemaName);
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public List<ColumnConfig> getColumns() {
        return columns;
    }

    public void setColumns(List<ColumnConfig> columns) {
        this.columns = columns;
    }

    public void addColumn(ColumnConfig column) {
        columns.add(column);
    }

    public void removeColumn(ColumnConfig column) {
        columns.remove(column);
    }

    public String getWhereClause() {
        return whereClause;
    }

    public void setWhereClause(String whereClause) {
        this.whereClause = whereClause;
    }

    public SqlStatement[] generateStatements(Database database) {

        UpdateStatement statement = new UpdateStatement(getSchemaName() == null?database.getDefaultSchemaName():getSchemaName(), getTableName());

        for (ColumnConfig column : columns) {
            statement.addNewColumnValue(column.getName(), column.getValueObject());
        }

        statement.setWhereClause(whereClause);

        return new SqlStatement[]{
                statement
        };
    }

    public String getConfirmationMessage() {
        return "Data updated in " + getTableName();
    }

}
