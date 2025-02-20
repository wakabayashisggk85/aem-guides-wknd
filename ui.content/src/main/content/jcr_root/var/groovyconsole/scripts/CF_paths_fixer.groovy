import com.day.cq.commons.jcr.JcrConstants
import com.day.cq.dam.api.DamConstants
import javax.jcr.*

def dryRun = true  // Set to false to apply changes

def rootPath = "/content/dam/b2c-website-foundation/content-fragments/ie/en_ie"  // Change this as needed
def pathSource1 = "/language-master/en_gb"
def pathSource2 = "/language-masters/en_gb"
def pathTarget = "/ie/en_ie"

def session = resourceResolver.adaptTo(Session)
def rootNode = session.getNode(rootPath)

// Use a map to store mutable counters
def counters = [nodesChecked: 0, changesMade: 0]

def scanNodes(Node node, def counters, def pathSource1, def pathSource2, def pathTarget, def dryRun) {
    if (!node) return

    counters.nodesChecked++  // Increment node check count

    // Check if the node is a dam:Asset
    if (node.isNodeType(DamConstants.NT_DAM_ASSET)) {
        def masterNodePath = "jcr:content/data/master"  // Standard path for master rendition
        if (node.hasNode(masterNodePath)) {
            def masterNode = node.getNode(masterNodePath)
            def propertiesChanged = []

            masterNode.getProperties().each { Property prop ->
                if (prop.definition.isProtected() || prop.definition.isAutoCreated()) return

                
                if (prop.type == PropertyType.STRING && !prop.isMultiple()) {
                    if (prop.getString().contains(pathSource1)) {
                        def value = prop.getString()
                        if (value.contains(pathSource1)) {
                            if (!dryRun) prop.setValue(value.replace(pathSource1, pathTarget))
                            propertiesChanged << prop.name
                            counters.changesMade++
                        }
                    }
                    if (prop.getString().contains(pathSource2)) {
                        def value = prop.getString()
                        if (value.contains(pathSource2)) {
                            if (!dryRun) prop.setValue(value.replace(pathSource2, pathTarget))
                            propertiesChanged << prop.name
                            counters.changesMade++
                        }
                    }
                } else if (prop.type == PropertyType.STRING && prop.isMultiple()) {
                    def values = prop.getValues()
                    def newValues1 = values.collect { it.string.replace(pathSource1, pathTarget) }
                    if (values*.string != newValues1) {
                        if (!dryRun) prop.setValue(newValues1 as String[])
                        propertiesChanged << prop.name
                        counters.changesMade++
                    }
                    def newValues2 = values.collect { it.string.replace(pathSource2, pathTarget) }
                    if (values*.string != newValues2) {
                        if (!dryRun) prop.setValue(newValues2 as String[])
                        propertiesChanged << prop.name
                        counters.changesMade++
                    }
                }
            }

            if (!propertiesChanged.isEmpty()) {
                println "Updated node: ${node.path} - properties changed: ${propertiesChanged}"
            }
        }
    }
    else {
        // Recursively scan child nodes
        node.getNodes().each { scanNodes(it, counters, pathSource1, pathSource2, pathTarget, dryRun) }
    }
}

// Start scanning from root node
scanNodes(rootNode, counters, pathSource1, pathSource2, pathTarget, dryRun)

// Save session if changes were made and dryRun is false
println "Total nodes checked: ${counters.nodesChecked}"
if (counters.changesMade > 0) {
    if (dryRun) {
        println "Dry Run Mode: No changes saved."
        session.refresh(false)  // Discard changes
    } else {
        session.save()
        println "Changes committed to JCR."
    }
    println "Total occurrences changed: ${counters.changesMade}"
} else {
    println "No occurrences of '${pathSource1}' or '${pathSource2}' found under '${rootPath}'."
}