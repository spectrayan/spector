
# Education


## Properties

Name | Type
------------ | -------------
`institution` | string
`degree` | string
`startYear` | number
`endYear` | number
`description` | string
`ongoing` | boolean

## Example

```typescript
import type { Education } from ''

// TODO: Update the object below with actual values
const example = {
  "institution": null,
  "degree": null,
  "startYear": null,
  "endYear": null,
  "description": null,
  "ongoing": null,
} satisfies Education

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as Education
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


