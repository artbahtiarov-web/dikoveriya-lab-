import ru.dikoveriya.lab.LabOperationRules

fun <T> rejects(label: String, block: ()->T) {
    try { block(); error("Expected validation to fail: $label") }
    catch(e:IllegalArgumentException) { /* expected */ }
}
fun main() {
    check(LabOperationRules.remainingAfterSplit(10,listOf(3,4),listOf(8,9),2)==1)
    check(LabOperationRules.remainingAfterSplit(10,listOf(10),listOf(25),0)==0)
    rejects("too many inputs"){LabOperationRules.remainingAfterSplit(5,listOf(3,3),listOf(4,4),0)}
    rejects("loss overflow"){LabOperationRules.remainingAfterSplit(5,listOf(5),listOf(5),1)}
    rejects("negative loss"){LabOperationRules.remainingAfterSplit(5,listOf(1),listOf(1),-1)}
    rejects("zero input"){LabOperationRules.remainingAfterSplit(5,listOf(0),listOf(2),0)}
    rejects("zero output"){LabOperationRules.remainingAfterSplit(5,listOf(2),listOf(0),0)}
    rejects("no daughters"){LabOperationRules.remainingAfterSplit(5,emptyList(),emptyList(),0)}
    rejects("too many daughters"){LabOperationRules.remainingAfterSplit(30,List(26){1},List(26){1},0)}
    rejects("mismatched arrays"){LabOperationRules.remainingAfterSplit(5,listOf(2),listOf(1,2),0)}
    check(LabOperationRules.remainingAfterMerge(listOf(10,20),listOf(5,19),7)==listOf(5,1))
    rejects("merge overflow"){LabOperationRules.remainingAfterMerge(listOf(10,5),listOf(7,6),3)}
    rejects("merge one source"){LabOperationRules.remainingAfterMerge(listOf(10),listOf(7),3)}
    rejects("merge zero output"){LabOperationRules.remainingAfterMerge(listOf(10,5),listOf(7,1),0)}
    check(LabOperationRules.remainingAfterLoss(10,3)==7)
    check(LabOperationRules.remainingAfterLoss(10,10)==0)
    rejects("loss larger than count"){LabOperationRules.remainingAfterLoss(10,11)}
    rejects("loss zero"){LabOperationRules.remainingAfterLoss(10,0)}
    println("PASS: 18 operation validation scenarios")
}
