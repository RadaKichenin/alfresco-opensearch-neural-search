// search.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class SearchService {
  private apiUrl = 'http://localhost:8081/search';

  constructor(private http: HttpClient) {}

  search(query: string, searchType: string, username: string, password: string): Observable<any[]> {
    const headers = new HttpHeaders({
      'Authorization': 'Basic ' + btoa(username + ':' + password)
    });

    return this.http.get<any[]>(
      `${this.apiUrl}?query=${encodeURIComponent(query)}&searchType=${searchType}`,
      { headers }
    );
  }
}