package net.corda.training.contract

import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import net.corda.training.state.IOUState

/**
 * This is where you'll add the contract code which defines how the [IOUState] behaves. Look at the unit tests in
 * [IOUContractTests] for instructions on how to complete the [IOUContract] class.
 */
class IOUContract : Contract {
    companion object {
        @JvmStatic
        val IOU_CONTRACT_ID = "net.corda.training.contract.IOUContract"
    }

    /**
     * Add any commands required for this contract as classes within this interface.
     * It is useful to encapsulate your commands inside an interface, so you can use the [requireSingleCommand]
     * function to check for a number of commands which implement this interface.
     */
    interface Commands : CommandData {
        // Add commands here.
        // E.g
        class Issue: TypeOnlyCommandData(), CommandData
    }

    /**
     * The contract code for the [IOUContract].
     * The constraints are self documenting so don't require any additional explanation.
     */
    override fun verify(tx: LedgerTransaction) {
        val refIssueCommand = tx.commands.requireSingleCommand<Commands.Issue>()

        requireThat {
            "No inputs should be consumed when issuing an IOU." using(tx.inputs.isEmpty())
            "Only one output state should be created when issuing an IOU." using(tx.outputs.size == 1)
            val refIOUState: IOUState = tx.outputStates.single() as IOUState;
            "A newly issued IOU must have a positive amount." using(refIOUState.amount.quantity > 0)
            "The lender and borrower cannot have the same identity." using(refIOUState.lender != refIOUState.borrower)
            val setOfParticipants = refIssueCommand.signers.toSet();
            "Both lender and borrower together only may sign IOU issue transaction." using(setOfParticipants == refIOUState.participants.map { it.owningKey }.toSet())
        }


        // Add contract code here.
        // requireThat {
        //     ...
        // }
    }
}