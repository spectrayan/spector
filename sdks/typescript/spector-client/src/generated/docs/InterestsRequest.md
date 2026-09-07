
# InterestsRequest


## Properties

Name | Type
------------ | -------------
`interests` | [Array&lt;InterestEntryDto&gt;](InterestEntryDto.md)
`disinterests` | [Array&lt;InterestEntryDto&gt;](InterestEntryDto.md)

## Example

```typescript
import type { InterestsRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "interests": null,
  "disinterests": null,
} satisfies InterestsRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as InterestsRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


