
# UserProfileDto


## Properties

Name | Type
------------ | -------------
`interestsList` | [Array&lt;InterestEntryDto&gt;](InterestEntryDto.md)
`disinterestsList` | [Array&lt;InterestEntryDto&gt;](InterestEntryDto.md)
`icnuWeights` | [IcnuDto](IcnuDto.md)
`alpha` | number
`beta` | number
`flashbulbThreshold` | number
`persona` | [PersonaContext](PersonaContext.md)

## Example

```typescript
import type { UserProfileDto } from ''

// TODO: Update the object below with actual values
const example = {
  "interestsList": null,
  "disinterestsList": null,
  "icnuWeights": null,
  "alpha": null,
  "beta": null,
  "flashbulbThreshold": null,
  "persona": null,
} satisfies UserProfileDto

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as UserProfileDto
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


