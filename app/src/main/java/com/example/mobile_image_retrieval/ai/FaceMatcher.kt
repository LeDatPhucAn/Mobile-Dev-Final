package com.example.mobile_image_retrieval.ai

object FaceModelContract {
    const val VERSION = "buffalo-sc-w600k-mbf-v1"
    const val DIMENSION = 512
    // Initial operating point, not a calibrated probability. Validate on target photos.
    const val MATCH_THRESHOLD = .45f
    const val DETECTOR_ASSET = "models/det_500m.onnx"
    const val RECOGNIZER_ASSET = "models/w600k_mbf.onnx"
}

object FaceMatcher {
    /** Every requested person must match a distinct detected face, including ambiguous candidates. */
    fun matchAll(people: List<FloatArray>, faces: List<FloatArray>, threshold: Float = FaceModelContract.MATCH_THRESHOLD): Float? {
        require(threshold in -1f..1f)
        if (people.isEmpty() || faces.size < people.size) return null
        val scores = people.map { person ->
            faces.map { face ->
                if (person.size != face.size) Float.NEGATIVE_INFINITY else VectorMath.dot(person, face)
            }
        }
        val choices = scores.map { row -> row.indices.filter { row[it] >= threshold }.sortedByDescending { row[it] } }
        if (choices.any { it.isEmpty() }) return null
        val assignedPerson = IntArray(faces.size) { -1 }
        fun assign(person: Int, visited: BooleanArray): Boolean {
            for (face in choices[person]) {
                if (visited[face]) continue
                visited[face] = true
                if (assignedPerson[face] == -1 || assign(assignedPerson[face], visited)) {
                    assignedPerson[face] = person
                    return true
                }
            }
            return false
        }
        for (person in people.indices.sortedBy { choices[it].size }) {
            if (!assign(person, BooleanArray(faces.size))) return null
        }
        return assignedPerson.indices.filter { assignedPerson[it] >= 0 }
            .map { face -> scores[assignedPerson[face]][face] }.average().toFloat()
    }
}
