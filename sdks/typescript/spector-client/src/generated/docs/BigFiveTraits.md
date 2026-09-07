
# BigFiveTraits


## Properties

Name | Type
------------ | -------------
`openness` | number
`conscientiousness` | number
`extraversion` | number
`agreeableness` | number
`neuroticism` | number
`neutral` | boolean

## Example

```typescript
import type { BigFiveTraits } from ''

// TODO: Update the object below with actual values
const example = {
  "openness": null,
  "conscientiousness": null,
  "extraversion": null,
  "agreeableness": null,
  "neuroticism": null,
  "neutral": null,
} satisfies BigFiveTraits

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as BigFiveTraits
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


