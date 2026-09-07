
# AnnotatedValue


## Properties

Name | Type
------------ | -------------
`value` | any
`source` | string

## Example

```typescript
import type { AnnotatedValue } from ''

// TODO: Update the object below with actual values
const example = {
  "value": null,
  "source": null,
} satisfies AnnotatedValue

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AnnotatedValue
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


