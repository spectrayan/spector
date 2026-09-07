
# SalienceSnapshot


## Properties

Name | Type
------------ | -------------
`interests` | [Array&lt;InterestEntry&gt;](InterestEntry.md)
`disinterests` | [Array&lt;InterestEntry&gt;](InterestEntry.md)
`icnuWeights` | [IcnuWeights](IcnuWeights.md)
`alpha` | number
`beta` | number
`hasPersona` | boolean
`isActive` | boolean

## Example

```typescript
import type { SalienceSnapshot } from ''

// TODO: Update the object below with actual values
const example = {
  "interests": null,
  "disinterests": null,
  "icnuWeights": null,
  "alpha": null,
  "beta": null,
  "hasPersona": null,
  "isActive": null,
} satisfies SalienceSnapshot

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as SalienceSnapshot
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


