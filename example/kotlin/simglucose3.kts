#!/usr/bin/env kscript

var signalStep = 1.0
val initScript = "./simglucose_example.py"

// This script depends on FalCAuN-core and FalCAuN-python
@file:DependsOn("net.maswag.falcaun:FalCAuN-core:1.0-SNAPSHOT", "net.maswag.falcaun:FalCAuN-python:1.0-SNAPSHOT")
// And requires JEP library
// Below is an example path to the JEP library when using pyenv and python 3.10.15
//@file:KotlinOptions("-Djava.library.path=$PYENV_ROOT/versions/3.10.15/lib/python3.10/site-packages/jep")

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import net.automatalib.modelchecker.ltsmin.AbstractLTSmin
import net.automatalib.modelchecker.ltsmin.LTSminVersion
import net.maswag.falcaun.*
import org.slf4j.LoggerFactory
import kotlin.streams.toList
import net.automatalib.word.Word;

// The following surprises the debug log
var updaterLogger = LoggerFactory.getLogger(AbstractAdaptiveSTLUpdater::class.java) as Logger
updaterLogger.level = Level.INFO
var updateListLogger = LoggerFactory.getLogger(AdaptiveSTLList::class.java) as Logger
updateListLogger.level = Level.INFO
var LTSminVersionLogger = LoggerFactory.getLogger(LTSminVersion::class.java) as Logger
LTSminVersionLogger.level = Level.INFO
var AbstractLTSminLogger = LoggerFactory.getLogger(AbstractLTSmin::class.java) as Logger
AbstractLTSminLogger.level = Level.INFO
var EQSearchProblemLogger = LoggerFactory.getLogger(EQSearchProblem::class.java) as Logger
EQSearchProblemLogger.level = Level.INFO
var SimulinkSteadyStateGeneticAlgorithmLogger = LoggerFactory.getLogger(EQSteadyStateGeneticAlgorithm::class.java) as Logger
SimulinkSteadyStateGeneticAlgorithmLogger.level = Level.INFO

// Define the input and output mappers
val ignoreValues = listOf(null)
val mealSizeValues = listOf(0.0, 50.0)
val inputMapper = InputMapperReader.make(listOf(mealSizeValues))
val bgValues = listOf(55.0, 180.0, 240.0)
val insulinValues = listOf(0.5)
val outputMapperReader = OutputMapperReader(listOf(bgValues, insulinValues, bgValues, ignoreValues, ignoreValues, ignoreValues))
outputMapperReader.parse()
val signalMapper = ExtendedSignalMapper()
val mapper =
    NumericSULMapper(inputMapper, outputMapperReader.largest, outputMapperReader.outputMapper, signalMapper)

val bg = "signal(0)"
val insulin = "signal(1)"
val min_bg = "signal(2)"
val max_bg = "signal(3)"
val alpha = 6 //30mins * alpha tick

// Define the STL properties
val stlFactory = STLFactory()
val stlList = listOf(
    // BG does not go below 55
    "[] ($min_bg > 55.0)",
    // Does not exceed the upper 10% for more than 30 minutes
    "(input(0) == 50.0 && X (input(0) == 50.0)) R ($bg > 180.0 -> X ($min_bg < 180.0))",
    // Does not exceed the upper 10% for more than 30 minutes after insulin administration
    "(input(0) == 50.0 && X (input(0) == 50.0)) R ($insulin > 0.5 -> X ($min_bg < 180.0))",
    // Hyperglycemia does not last longer than 180 minutes
    "(<> (input(0) == 50.0 && X (input(0) == 50.0))) R ! []_[0,$alpha] ($min_bg > 240.0)",
).stream().map { stlString ->
    stlFactory.parse(
        stlString,
        inputMapper,
        outputMapperReader.outputMapper,
        outputMapperReader.largest
    )
}.toList()
val signalLength = 48 //3*10 * 24 mins
val properties = AdaptiveSTLList(stlList, signalLength)

// Constants for the GA-based equivalence testing
val maxTest = 50000
val populationSize = 200
val crossoverProb = 0.5
val mutationProb = 0.01

// Load the simglucose model implemented by python
PythonNumericSUL(initScript).use { sul ->
    // Configure and run the verifier
    val verifier = NumericSULVerifier(sul, signalStep, properties, mapper)
    // Timeout must be set before adding equivalence testing
    verifier.setTimeout(5 * 60 * 8) // 5 minutes
    verifier.addCornerCaseEQOracle(signalLength, signalLength / 2);
    verifier.addGAEQOracleAll(
        signalLength,
        maxTest,
        ArgParser.GASelectionKind.Tournament,
        populationSize,
        crossoverProb,
        mutationProb
    )
    val result = verifier.run()

    // Print the result
    if (result) {
        println("The property is likely satisfied")
    } else {
        for (i in 0 until verifier.cexProperty.size) {
            println("${verifier.cexProperty[i]} is falsified by the following counterexample")
            println("cex concrete input: ${verifier.cexConcreteInput[i]}")
            println("cex abstract input: ${verifier.cexAbstractInput[i]}")
            println("cex output: ${verifier.cexOutput[i]}")

            val inputWord = Word.fromList(verifier.cexConcreteInput[i].getSignalValues())
            val concreteOutput = sul.execute(inputWord).getOutputSignal().asList()
            println("cex concrete output: ${concreteOutput}")
        }
    }
    println("Execution time for simulation: ${verifier.simulationTimeSecond} [sec]")
    println("Number of simulations: ${verifier.simulinkCount}")
    println("Number of simulations for equivalence testing: ${verifier.simulinkCountForEqTest}")
}
