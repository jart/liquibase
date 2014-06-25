package liquibase.actiongenerator

import liquibase.ExecutionEnvironment
import liquibase.action.Action
import  liquibase.ExecutionEnvironment
import liquibase.sdk.database.MockDatabase
import liquibase.exception.ValidationErrors
import liquibase.statement.core.MockSqlStatement
import spock.lang.Specification
import org.junit.Test

public class ActionGeneratorChainTest extends Specification {

    def void generateActions_nullGenerators() {
        when:
        ActionGeneratorChain chain = new ActionGeneratorChain(null)

        then:
        chain.generateActions(new MockSqlStatement(), new ExecutionEnvironment(new MockDatabase())) == null
    }

    def generateActions_noGenerators() {
        when:
        SortedSet<ActionGenerator> generators = new TreeSet<ActionGenerator>(new ActionGeneratorComparator())
        ActionGeneratorChain chain =new ActionGeneratorChain(generators)

        then:
        assert chain.generateActions(new MockSqlStatement(), new ExecutionEnvironment(new MockDatabase())).length == 0
    }

   def generateActions_oneGenerators() {
       when:
        SortedSet<ActionGenerator> generators = new TreeSet<ActionGenerator>(new ActionGeneratorComparator())
        generators.add(new MockActionGenerator(1, "A1", "A2"))
        ActionGeneratorChain chain =new ActionGeneratorChain(generators)

        Action[] actions = chain.generateActions(new MockSqlStatement(), new ExecutionEnvironment(new MockDatabase()))
       then:
        assert actions.length == 2
        assert actions[0].describe() == "A1;"
        assert actions[1].describe() == "A2;"
    }

    def void generateActions_twoGenerators() {
        when:
        SortedSet<ActionGenerator> generators = new TreeSet<ActionGenerator>(new ActionGeneratorComparator())
        generators.add(new MockActionGenerator(2, "B1", "B2"))
        generators.add(new MockActionGenerator(1, "A1", "A2"))
        ActionGeneratorChain chain =new ActionGeneratorChain(generators)
        Action[] actions = chain.generateActions(new MockSqlStatement(), new ExecutionEnvironment(new MockDatabase()))
        
        then:
        assert actions.length == 4
        assert actions[0].describe() == "B1;"
        assert actions[1].describe() == "B2;"
        assert actions[2].describe() == "A1;"
        assert actions[3].describe() == "A2;"
    }

    def void generateActions_threeGenerators() {
        when:
        SortedSet<ActionGenerator> generators = new TreeSet<ActionGenerator>(new ActionGeneratorComparator())
        generators.add(new MockActionGenerator(2, "B1", "B2"))
        generators.add(new MockActionGenerator(1, "A1", "A2"))
        generators.add(new MockActionGenerator(3, "C1", "C2"))
        ActionGeneratorChain chain =new ActionGeneratorChain(generators)
        Action[] actions = chain.generateActions(new MockSqlStatement(), new ExecutionEnvironment(new MockDatabase()))
        
        then:
        assert actions.length == 6
        assert actions[0].describe() == "C1;"
        assert actions[1].describe() == "C2;"
        assert actions[2].describe() == "B1;"
        assert actions[3].describe() == "B2;"
        assert actions[4].describe() == "A1;"
        assert actions[5].describe() == "A2;"
    }

    def validate_nullGenerators() {
        when:
        ActionGeneratorChain chain = new ActionGeneratorChain(null)
        ValidationErrors validationErrors = chain.validate(new MockSqlStatement(), new ExecutionEnvironment(new MockDatabase()))
        
        then:
        assert !validationErrors.hasErrors()
    }

    def validate_oneGenerators_noErrors() {
        when:
        SortedSet<ActionGenerator> generators = new TreeSet<ActionGenerator>(new ActionGeneratorComparator())
        generators.add(new MockActionGenerator(1, "A1", "A2"))

        ActionGeneratorChain chain =new ActionGeneratorChain(generators)
        ValidationErrors validationErrors = chain.validate(new MockSqlStatement(), new ExecutionEnvironment(new MockDatabase()))
        
        then:
        assert !validationErrors.hasErrors()
    }

    @Test
    public void validate_oneGenerators_hasErrors() {
        when:
        SortedSet<ActionGenerator> generators = new TreeSet<ActionGenerator>(new ActionGeneratorComparator())
        generators.add(new MockActionGenerator(1, "A1", "A2").addValidationError("E1"))

        ActionGeneratorChain chain =new ActionGeneratorChain(generators)
        ValidationErrors validationErrors = chain.validate(new MockSqlStatement(), new ExecutionEnvironment(new MockDatabase()))

        then:
        assert validationErrors.hasErrors()
    }

    def validate_twoGenerators_noErrors() {
        when:
        SortedSet<ActionGenerator> generators = new TreeSet<ActionGenerator>(new ActionGeneratorComparator())
        generators.add(new MockActionGenerator(2, "B1", "B2"))
        generators.add(new MockActionGenerator(1, "A1", "A2"))

        ActionGeneratorChain chain =new ActionGeneratorChain(generators)
        ValidationErrors validationErrors = chain.validate(new MockSqlStatement(), new ExecutionEnvironment(new MockDatabase()))

        then:
        assert !validationErrors.hasErrors()
    }

    def validate_twoGenerators_firstHasErrors() {
        when:
        SortedSet<ActionGenerator> generators = new TreeSet<ActionGenerator>(new ActionGeneratorComparator())
        generators.add(new MockActionGenerator(2, "B1", "B2").addValidationError("E1"))
        generators.add(new MockActionGenerator(1, "A1", "A2"))

        ActionGeneratorChain chain =new ActionGeneratorChain(generators)
        ValidationErrors validationErrors = chain.validate(new MockSqlStatement(), new ExecutionEnvironment(new MockDatabase()))

        then:
        assert validationErrors.hasErrors()
    }

    def validate_twoGenerators_secondHasErrors() {
        when:
        SortedSet<ActionGenerator> generators = new TreeSet<ActionGenerator>(new ActionGeneratorComparator())
        generators.add(new MockActionGenerator(2, "B1", "B2"))
        generators.add(new MockActionGenerator(1, "A1", "A2").addValidationError("E1"))

        ActionGeneratorChain chain =new ActionGeneratorChain(generators)
        ValidationErrors validationErrors = chain.validate(new MockSqlStatement(), new ExecutionEnvironment(new MockDatabase()))

        then:
        assert validationErrors.hasErrors()
    }
}