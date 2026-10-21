
# FactAssertRequest


## Properties

Name | Type
------------ | -------------
`subject` | string
`predicate` | string
`object` | string
`validFrom` | number
`validTo` | number
`confidence` | number
`allowCoexisting` | boolean

## Example

```typescript
import type { FactAssertRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "subject": null,
  "predicate": null,
  "object": null,
  "validFrom": null,
  "validTo": null,
  "confidence": null,
  "allowCoexisting": null,
} satisfies FactAssertRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as FactAssertRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


