import com.day.cq.commons.jcr.JcrConstants
import com.day.cq.dam.api.DamConstants
import javax.jcr.*

def dryRun = true  // Set to false to apply changes

def rootPath = "/content/experience-fragments/b2c/ie/en"  // Change this as needed
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

    // Check if the node has the specified resourceType
    if (node.hasProperty("sling:resourceType") && node.getProperty("sling:resourceType").getString() == "b2c-website-foundation/components/xfpage") {
        if (node.hasNode("root")) {
            def rootNode = node.getNode("root")
            scanSubnodes(rootNode, counters, pathSource1, pathSource2, pathTarget, dryRun)
        }
    } else {
        // Recursively scan child nodes
        node.getNodes().each { scanNodes(it, counters, pathSource1, pathSource2, pathTarget, dryRun) }
    }
}

def scanSubnodes(Node node, def counters, def pathSource1, def pathSource2, def pathTarget, def dryRun) {
    node.getNodes().each { subnode ->
        def propertiesChanged = []

        subnode.getProperties().each { Property prop ->
            if (prop.definition.isProtected() || prop.definition.isAutoCreated()) return

            if (prop.type == PropertyType.STRING && !prop.isMultiple()) {
                def value = prop.getString()
                if (value.contains(pathSource1) || value.contains(pathSource2)) {
                    if (!dryRun) prop.setValue(value.replace(pathSource1, pathTarget).replace(pathSource2, pathTarget))
                    propertiesChanged << prop.name
                    counters.changesMade++
                }
            } else if (prop.type == PropertyType.STRING && prop.isMultiple()) {
                def values = prop.getValues()
                def newValues = values.collect { it.string.replace(pathSource1, pathTarget).replace(pathSource2, pathTarget) }
                if (values*.string != newValues) {
                    if (!dryRun) prop.setValue(newValues as String[])
                    propertiesChanged << prop.name
                    counters.changesMade++
                }
            }
        }

        if (!propertiesChanged.isEmpty()) {
            println "Updated node: ${subnode.path} - properties changed: ${propertiesChanged}"
        }

        scanSubnodes(subnode, counters, pathSource1, pathSource2, pathTarget, dryRun)
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