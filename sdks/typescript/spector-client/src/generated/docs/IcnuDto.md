
# IcnuDto


## Properties

Name | Type
------------ | -------------
`interest` | number
`challenge` | number
`novelty` | number
`urgency` | number

## Example

```typescript
import type { IcnuDto } from ''

// TODO: Update the object below with actual values
const example = {
  "interest": null,
  "challenge": null,
  "novelty": null,
  "urgency": null,
} satisfies IcnuDto

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as IcnuDto
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


