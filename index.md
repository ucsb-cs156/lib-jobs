<!-- markdownlint-disable MD033 MD041 -->
<!-- disabling MD033 allows inline html -->
<!-- disabling MD041 allows starting with something other than an H1 -->

<style>
table, th, td {
  border: 1px solid black;
  padding: 2px;
  border-collapse: collapse;
}
tbody tr:nth-child(even) {background-color: #f2f2f2;}
</style>

* Source Repo: <https://github.com/{{site.repo}}>
  * gh-pages: <https://github.com/{{site.repo}}/tree/gh-pages>
* Github Actions: <https://github.com/{{site.repo}}/actions>

This repo is a backend-only Maven library (the `@ucsb-cs156/jobs-components`
npm package will live in `frontend/` in a later phase), so only the backend
documentation and coverage reports are published here.

## Documentation

<table>
<thead>
<tr>
<th colspan="1" style="text-align:center">Backend</th>
</tr>
</thead>
<tbody>
<tr>
<td><a href="javadoc">javadoc</a></td>
</tr>
</tbody>
</table>

## Test Coverage

<table>
<thead>
<tr>
<th colspan="2" style="text-align:center">Backend</th>
</tr>
<tr>
<th>Jacoco</th>
<th>Pitest</th>
</tr>
</thead>
<tbody>
<tr>
<td><a href="jacoco">jacoco</a></td>
<td><a href="pitest">pitest</a></td>
</tr>
</tbody>
</table>

## Open Pull Requests

### Documentation for PRs

<table>
<thead>
<tr>
<th colspan="3" style="text-align:center">Pull Request</th>
<th colspan="1" style="text-align:center">Backend</th>
</tr>
<tr>
<th>PR</th>
<th>Branch</th>
<th>Author</th>
<th>Javadoc</th>
</tr>
</thead>
<tbody>
{% for pr in site.pull_requests %}
<tr>
<td><a href="{{pr.url}}">PR {{pr.number}}</a></td>
<td>{{pr.headRefName}}</td>
<td>{{pr.author.login}}</td>
<td><a href="prs/{{pr.number}}/javadoc">javadoc</a></td>
</tr>
{% endfor %}
</tbody>
</table>

### Test Coverage for PRs

<table>
<thead>
<tr>
<th colspan="3" style="text-align:center">Pull Request</th>
<th colspan="2" style="text-align:center">Backend</th>
</tr>
<tr>
<th>PR</th>
<th>Branch</th>
<th>Author</th>
<th>Jacoco</th>
<th>Pitest</th>
</tr>
</thead>
<tbody>
{% for pr in site.pull_requests %}
<tr>
<td><a href="{{pr.url}}">PR {{pr.number}}</a></td>
<td>{{pr.headRefName}}</td>
<td>{{pr.author.login}}</td>
<td><a href="prs/{{pr.number}}/jacoco">jacoco</a></td>
<td><a href="prs/{{pr.number}}/pitest">pitest</a></td>
</tr>
{% endfor %}
</tbody>
</table>

## Notes

If links in the PR tables don't work, note the following:

* Backend links may not be updated for PRs that do not touch the backend code.
* If a link doesn't work when you expect that it should, check that the appropriate [Github Actions](https://github.com/{{site.repo}}/actions) workflow completed successfully.
* You can also check the contents of the [gh-pages branch of this repo](https://github.com/{{site.repo}}/tree/gh-pages) to see if they were updated with the appropriate directory.
* Note that the pitest runs that are triggered by PRs and by workflow 2 compute
  incremental pitest results based on stored history.  It is rare, but this may
  occasionally be different from the results when doing a full pitest run from
  scratch, which is done every time a push is made to the main branch (for example,
  when merging a PR).
