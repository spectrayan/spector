
# MelExplainRow


## Properties

Name | Type
------------ | -------------
`rank` | number
`memoryId` | string
`tier` | string
`fusedScore` | number
`vectorSimilarity` | number
`bm25Score` | number
`recencyDecay` | number
`cognitiveMass` | number
`storageStrength` | number
`valenceFactor` | number
`hebbianBoost` | number
`freeEnergyPenalty` | number
`signal` | string
`rawScore` | number
`weight` | number
`contribution` | number

## Example

```typescript
import type { MelExplainRow } from ''

// TODO: Update the object below with actual values
const example = {
  "rank": null,
  "memoryId": null,
  "tier": null,
  "fusedScore": null,
  "vectorSimilarity": null,
  "bm25Score": null,
  "recencyDecay": null,
  "cognitiveMass": null,
  "storageStrength": null,
  "valenceFactor": null,
  "hebbianBoost": null,
  "freeEnergyPenalty": null,
  "signal": null,
  "rawScore": null,
  "weight": null,
  "contribution": null,
} satisfies MelExplainRow

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as MelExplainRow
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


