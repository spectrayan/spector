
# StylometricFeatures


## Properties

Name | Type
------------ | -------------
`meanSentenceLength` | number
`sentenceLengthVariance` | number
`typeTokenRatio` | number
`clauseComplexity` | number
`commaRate` | number
`dashRate` | number
`ellipsisRate` | number
`exclamationRate` | number
`questionRate` | number
`formalityScore` | number

## Example

```typescript
import type { StylometricFeatures } from ''

// TODO: Update the object below with actual values
const example = {
  "meanSentenceLength": null,
  "sentenceLengthVariance": null,
  "typeTokenRatio": null,
  "clauseComplexity": null,
  "commaRate": null,
  "dashRate": null,
  "ellipsisRate": null,
  "exclamationRate": null,
  "questionRate": null,
  "formalityScore": null,
} satisfies StylometricFeatures

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as StylometricFeatures
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


